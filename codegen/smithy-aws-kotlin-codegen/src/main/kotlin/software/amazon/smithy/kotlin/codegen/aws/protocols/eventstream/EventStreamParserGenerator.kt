/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.aws.protocols.eventstream

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.ExceptionBaseClassGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.bodyDeserializer
import software.amazon.smithy.kotlin.codegen.rendering.serde.bodyDeserializerName
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EventHeaderTrait
import software.amazon.smithy.model.traits.EventPayloadTrait
import software.amazon.smithy.model.traits.StreamingTrait

/**
 * A set of RPC-bound Smithy protocols
 */
val RPC_BOUND_PROTOCOLS = setOf(
    "awsJson1_0",
    "awsJson1_1",
    "awsQuery",
    "ec2Query",
)

/**
 * Represents whether the given ShapeId represents an RPC-bound Smithy protocol
 */
internal val ShapeId.isRpcBoundProtocol: Boolean
    get() = RPC_BOUND_PROTOCOLS.contains(name)

/**
 * Implements rendering deserialize implementation for event streams implemented using the
 * `vnd.amazon.event-stream` content-type
 *
 * @param sdg the structured data parser generator
 */
class EventStreamParserGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val sdg: StructuredDataParserGenerator,
) {
    /**
     * Return the function responsible for deserializing an operation output that targets an event stream
     *
     * ```
     * private suspend fun deserializeFooOperationBody(builder: Foo.Builder, body: HttpBody) { ... }
     * ```
     */
    fun responseHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol =
        op.bodyDeserializer(ctx.settings) { writer ->
            val outputSymbol = ctx.symbolProvider.toSymbol(ctx.model.expectShape<StructureShape>(op.output.get()))
            // we have access to the builder for the output type and the full HttpBody
            // members bound via HTTP bindings (e.g. httpHeader, statusCode, etc) are already deserialized via HttpDeserialize impl
            // we just need to deserialize the event stream member (and/or the initial response)
            writer.withBlock(
                // FIXME - revert to private, exposed as internal temporarily while we figure out integration tests
                "internal suspend fun #L(builder: #T.Builder, call: #T) {",
                "}",
                op.bodyDeserializerName(),
                outputSymbol,
                RuntimeTypes.Http.HttpCall,
            ) {
                renderDeserializeEventStream(ctx, op, writer)
            }
        }

    private fun renderDeserializeEventStream(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val output = ctx.model.expectShape<StructureShape>(op.output.get())
        val streamingMember = output.findStreamingMember(ctx.model) ?: error("expected a streaming member for $output")
        val streamShape = ctx.model.expectShape<UnionShape>(streamingMember.target)
        val streamSymbol = ctx.symbolProvider.toSymbol(streamShape)

        val messageTypeSymbol = RuntimeTypes.AwsEventStream.MessageType
        val baseExceptionSymbol = ExceptionBaseClassGenerator.baseExceptionSymbol(ctx.settings)

        writer.write("val chan = call.response.body.#T(call) ?: return", RuntimeTypes.Http.toSdkByteReadChannel)
        writer.write("val frames = #T(chan)", RuntimeTypes.AwsEventStream.decodeFrames)
        if (ctx.protocol.isRpcBoundProtocol) {
            renderDeserializeInitialResponse(ctx, output, writer)
        } else {
            writer.write("val events = frames")
        }
        writer.indent()
            .withBlock(".#T { message ->", "}", RuntimeTypes.KotlinxCoroutines.Flow.map) {
                withBlock("when (val mt = message.#T()) {", "}", RuntimeTypes.AwsEventStream.MessageTypeExt) {
                    withBlock("is #T.Event -> when (mt.shapeType) {", "}", messageTypeSymbol) {
                        streamShape.filterEventStreamErrors(ctx.model).forEach { member ->
                            withBlock("#S -> {", "}", member.memberName) {
                                renderDeserializeEventVariant(ctx, streamSymbol, member, writer)
                            }
                        }

                        write("else -> #T.SdkUnknown", streamSymbol)
                    }
                    withBlock("is #T.Exception -> when (mt.shapeType) {", "}", messageTypeSymbol) {
                        // errors are completely bound to payload (at least according to design docs)
                        val errorMembers = streamShape.members().filter {
                            val target = ctx.model.expectShape(it.target)
                            target.isError
                        }
                        errorMembers.forEach { member ->
                            withBlock("#S -> {", "}", member.memberName) {
                                val payloadDeserializeFn = sdg.payloadDeserializer(ctx, member)
                                write("val err = #T(message.payload)", payloadDeserializeFn)
                                write("throw err")
                            }
                        }
                        write("else -> throw #T(#S)", baseExceptionSymbol, "error processing event stream, unrecognized errorType: \${mt.shapeType}")
                    }
                    // this is a service exception still, just un-modeled
                    write("is #T.Error -> throw #T(\"error processing event stream: errorCode=\${mt.errorCode}; message=\${mt.message}\")", messageTypeSymbol, baseExceptionSymbol)
                    // this is a client exception because we failed to parse it
                    write("is #T.SdkUnknown -> throw #T(\"unrecognized event stream message `:message-type`: \${mt.messageType}\")", messageTypeSymbol, RuntimeTypes.Core.ClientException)
                }
            }
            .dedent()
            .write("")

        writer.write("builder.#L = events", streamingMember.defaultName())
    }

    private fun renderDeserializeEventVariant(ctx: ProtocolGenerator.GenerationContext, unionSymbol: Symbol, member: MemberShape, writer: KotlinWriter) {
        val variant = ctx.model.expectShape(member.target)

        val eventHeaderBindings = variant.members().filter { it.hasTrait<EventHeaderTrait>() }
        val eventPayloadBinding = variant.members().firstOrNull { it.hasTrait<EventPayloadTrait>() }
        val unbound = variant.members().filterNot { it.hasTrait<EventHeaderTrait>() || it.hasTrait<EventPayloadTrait>() }

        if (eventHeaderBindings.isEmpty() && eventPayloadBinding == null) {
            // the entire variant can be deserialized from the payload
            val payloadDeserializeFn = sdg.payloadDeserializer(ctx, member)
            writer.write("val e = #T(message.payload)", payloadDeserializeFn)
        } else {
            val variantSymbol = ctx.symbolProvider.toSymbol(variant)
            writer.write("val eb = #T.Builder()", variantSymbol)

            // render members bound to header
            eventHeaderBindings.forEach { hdrBinding ->
                val target = ctx.model.expectShape(hdrBinding.target)
                val targetSymbol = ctx.symbolProvider.toSymbol(target)

                // :test(boolean, byte, short, integer, long, blob, string, timestamp))
                val conversionFn = when (target.type) {
                    ShapeType.BOOLEAN -> RuntimeTypes.AwsEventStream.expectBool
                    ShapeType.BYTE -> RuntimeTypes.AwsEventStream.expectByte
                    ShapeType.SHORT -> RuntimeTypes.AwsEventStream.expectInt16
                    ShapeType.INTEGER -> RuntimeTypes.AwsEventStream.expectInt32
                    ShapeType.LONG -> RuntimeTypes.AwsEventStream.expectInt64
                    ShapeType.BLOB -> RuntimeTypes.AwsEventStream.expectByteArray
                    ShapeType.STRING -> RuntimeTypes.AwsEventStream.expectString
                    ShapeType.TIMESTAMP -> RuntimeTypes.AwsEventStream.expectTimestamp
                    else -> throw CodegenException("unsupported eventHeader shape: member=$hdrBinding; targetShape=$target")
                }

                val defaultValuePostfix = if (targetSymbol.isNotNullable && targetSymbol.defaultValue() != null) {
                    " ?: ${targetSymbol.defaultValue()}"
                } else {
                    ""
                }
                writer.write("eb.#L = message.headers.find { it.name == #S }?.value?.#T()$defaultValuePostfix", hdrBinding.defaultName(), hdrBinding.memberName, conversionFn)
            }

            if (eventPayloadBinding != null) {
                renderDeserializeExplicitEventPayloadMember(ctx, eventPayloadBinding, writer)
            } else {
                if (unbound.isNotEmpty()) {
                    // all remaining members are bound to payload (but not explicitly bound via @eventPayload)
                    // generate a payload deserializer specific to the unbound members (note this will be a deserializer
                    // for the overall event shape but only payload members will be considered for deserialization),
                    // and then assign each deserialized payload member to the current builder instance
                    val payloadDeserializeFn = sdg.payloadDeserializer(ctx, member, unbound)
                    writer.write("val tmp = #T(message.payload)", payloadDeserializeFn)
                    unbound.forEach {
                        writer.write("eb.#1L = tmp.#1L", it.defaultName())
                    }
                }
            }

            writer.write("val e = eb.build()")
        }

        writer.write("#T.#L(e)", unionSymbol, member.unionVariantName())
    }

    /**
     * Renders deserialization logic for a message with the `initial-response` type.
     */
    private fun renderDeserializeInitialResponse(ctx: ProtocolGenerator.GenerationContext, outputShape: StructureShape, writer: KotlinWriter) {
        // A custom function which only deserializes the initial response members
        val initialResponseDeserializeFn = sdg.payloadDeserializer(ctx, outputShape, outputShape.initialResponseMembers)

        writer.write(
            "val firstMessage = frames.#T(1).#T()",
            RuntimeTypes.KotlinxCoroutines.Flow.take,
            RuntimeTypes.KotlinxCoroutines.Flow.single,
        )
        writer.write("val firstMessageType = firstMessage.type()")
        writer.openBlock(
            "val events = if (firstMessageType is #T.Event && firstMessageType.shapeType == \"initial-response\") {",
            RuntimeTypes.AwsEventStream.MessageType,
        )
        // Deserialize into `initialResponse`, then apply it to the actual response builder
        writer.write("val initialResponse = #T(firstMessage.payload)", initialResponseDeserializeFn)
        writer.withBlock("builder.apply {", "}") {
            outputShape.initialResponseMembers.forEach { member ->
                writer.write("#1L = initialResponse.#1L", member.defaultName())
            }
        }
            .write("frames")
            .closeAndOpenBlock("} else {")
            .write(
                "#T(#T(firstMessage), frames)",
                RuntimeTypes.Core.Utils.mergeSequential,
                RuntimeTypes.KotlinxCoroutines.Flow.flowOf,
            )
            .closeBlock("}")
    }

    /**
     * Get all the shape's members which aren't an event stream
     */
    private val StructureShape.initialResponseMembers get() = members().filter {
        val targetShape = ctx.model.getShape(it.target).getOrNull()
        targetShape?.hasTrait<StreamingTrait>() == false
    }

    private fun renderDeserializeExplicitEventPayloadMember(
        ctx: ProtocolGenerator.GenerationContext,
        member: MemberShape,
        writer: KotlinWriter,
    ) {
        // TODO - check content type for blob and string
        // structure > :test(member > :test(blob, string, structure, union))
        val target = ctx.model.expectShape(member.target)
        when (target.type) {
            ShapeType.BLOB -> writer.write("eb.#L = message.payload", member.defaultName())
            ShapeType.STRING -> writer.write("eb.#L = message.payload.decodeToString()", member.defaultName())
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                val payloadDeserializeFn = sdg.payloadDeserializer(ctx, member)
                writer.write("eb.#L = #T(message.payload)", member.defaultName(), payloadDeserializeFn)
            }
            else -> throw CodegenException("unsupported shape type `${target.type}` for target: $target; expected blob, string, structure, or union for eventPayload member: $member")
        }
    }
}
