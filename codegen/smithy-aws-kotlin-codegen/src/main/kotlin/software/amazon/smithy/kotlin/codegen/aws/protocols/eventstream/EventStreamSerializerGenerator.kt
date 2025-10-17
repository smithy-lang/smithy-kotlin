/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.aws.protocols.eventstream

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EventHeaderTrait
import software.amazon.smithy.model.traits.EventPayloadTrait
import software.amazon.smithy.model.traits.StreamingTrait

/**
 * Implements rendering serialize implementation for event streams implemented using the
 * `vnd.amazon.event-stream` content-type
 *
 * @param sdg the structured data serializer generator
 * @param payloadContentType the content-type to use when sending structured data (e.g. `application/json`)
 */
class EventStreamSerializerGenerator(
    private val sdg: StructuredDataSerializerGenerator,
    private val payloadContentType: String,
) {

    /**
     * Return the function responsible for serializing an operation output that targets an event stream
     *
     * ```
     * private suspend fun serializeFooOperationBody(input: FooInput): HttpBody { ... }
     * ```
     */
    fun requestHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol =
        op.bodySerializer(ctx.settings) { writer ->
            val inputSymbol = ctx.symbolProvider.toSymbol(ctx.model.expectShape<StructureShape>(op.input.get()))
            writer.withBlock(
                // FIXME - revert to private, exposed as internal temporarily while we figure out integration tests
                "internal suspend fun #L(context: #T, input: #T): #T {",
                "}",
                op.bodySerializerName(),
                RuntimeTypes.Core.ExecutionContext,
                inputSymbol,
                RuntimeTypes.Http.HttpBody,
            ) {
                renderSerializeEventStream(ctx, op, writer)
            }
        }

    private fun renderSerializeEventStream(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        val input = ctx.model.expectShape<StructureShape>(op.input.get())
        val streamingMember = input.findStreamingMember(ctx.model) ?: error("expected a streaming member for $input")
        val streamShape = ctx.model.expectShape<UnionShape>(streamingMember.target)

        writer.write("val stream = input.#L ?: return #T.Empty", streamingMember.defaultName(), RuntimeTypes.Http.HttpBody)

        // initial HTTP request should use an empty body hash since the actual body is the event stream
        writer.write("context[#T.HashSpecification] = #T.EmptyBody", RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes, RuntimeTypes.Auth.Signing.AwsSigningCommon.HashSpecification)

        // FIXME - we need a signer implementation which usually comes from the auth scheme...for now default to default signer for event streams
        writer.write("context[#T.Signer] = #T", RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes, RuntimeTypes.Auth.Signing.AwsSigningStandard.DefaultAwsSigner)
        // ensure a deferred is set, signer will complete it when initial request signature is available
        writer.write(
            "context[#T.RequestSignature] = #T(context.coroutineContext.#T)",
            RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
            RuntimeTypes.KotlinxCoroutines.CompletableDeferred,
            RuntimeTypes.KotlinxCoroutines.job,
        )

        val encodeFn = encodeEventStreamMessage(ctx, op, streamShape)

        writer.write("")
        val initialRequestMembers = input.initialRequestMembers(ctx)
        if (ctx.protocol.isRpcBoundProtocol && initialRequestMembers.isNotEmpty()) {
            val serializerFn = sdg.payloadSerializer(ctx, input, initialRequestMembers)

            writer.withBlock("val initialRequest = buildMessage {", "}") {
                writer.write("addHeader(\":message-type\", HeaderValue.String(\"event\"))")
                writer.write("addHeader(\":event-type\", HeaderValue.String(\"initial-request\"))")
                writer.write("payload = #T(input)", serializerFn)
            }

            writer.withBlock(
                "val messages = #T(#T(initialRequest), stream.#T(::#T))",
                "",
                RuntimeTypes.Core.Utils.mergeSequential,
                RuntimeTypes.KotlinxCoroutines.Flow.flowOf,
                RuntimeTypes.KotlinxCoroutines.Flow.map,
                encodeFn,
            ) {
                write(".#T(context)", RuntimeTypes.AwsEventStream.sign)
                write(".#T()", RuntimeTypes.AwsEventStream.encode)
            }
        } else {
            writer.withBlock("val messages = stream", "") {
                write(".#T(::#T)", RuntimeTypes.KotlinxCoroutines.Flow.map, encodeFn)
                write(".#T(context)", RuntimeTypes.AwsEventStream.sign)
                write(".#T()", RuntimeTypes.AwsEventStream.encode)
            }
        }

        writer.write("")
        writer.write("return messages.#T(context)", RuntimeTypes.AwsEventStream.asEventStreamHttpBody)
    }

    private fun encodeEventStreamMessage(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        streamShape: UnionShape,
    ): Symbol = buildSymbol {
        val streamSymbol = ctx.symbolProvider.toSymbol(streamShape)
        val fnName = "encode${op.capitalizedDefaultName()}${streamSymbol.name}EventMessage"
        name = fnName
        namespace = ctx.settings.pkg.serde
        // place it in same file as the operation serializer
        definitionFile = "${op.serializerName()}.kt"

        renderBy = { writer ->
            // TODO - make internal and share across operations?
            writer.withBlock(
                "private fun #L(input: #T): #T = #T {",
                "}",
                fnName,
                streamSymbol,
                RuntimeTypes.AwsEventStream.Message,
                RuntimeTypes.AwsEventStream.buildMessage,
            ) {
                addStringHeader(":message-type", "event")

                withBlock("when(input) {", "}") {
                    streamShape.filterEventStreamErrors(ctx.model)
                        .forEach { member ->
                            withBlock(
                                "is #T.#L -> {",
                                "}",
                                streamSymbol,
                                member.unionVariantName(),
                            ) {
                                addStringHeader(":event-type", member.memberName)
                                val variant = ctx.model.expectShape(member.target)

                                val eventHeaderBindings = variant.members().filter { it.hasTrait<EventHeaderTrait>() }
                                val eventPayloadBinding = variant.members().firstOrNull { it.hasTrait<EventPayloadTrait>() }
                                val unbound = variant.members().filterNot { it.hasTrait<EventHeaderTrait>() || it.hasTrait<EventPayloadTrait>() }

                                eventHeaderBindings.forEach { renderSerializeEventHeader(ctx, it, writer) }

                                when {
                                    eventPayloadBinding != null -> renderSerializeEventPayload(ctx, eventPayloadBinding, writer)
                                    unbound.isNotEmpty() -> {
                                        writer.addStringHeader(":content-type", payloadContentType)
                                        // get a payload serializer for the given members of the variant
                                        val serializeFn = sdg.payloadSerializer(ctx, variant, unbound)
                                        writer.write("payload = #T(input.value)", serializeFn)
                                    }
                                }
                            }
                        }
                    write("is #T.SdkUnknown -> error(#S)", streamSymbol, "cannot serialize the unknown event type!")
                }
            }
        }
    }

    private fun renderSerializeEventHeader(ctx: ProtocolGenerator.GenerationContext, member: MemberShape, writer: KotlinWriter) {
        val target = ctx.model.expectShape(member.target)
        val headerValue = when (target.type) {
            ShapeType.BOOLEAN -> "Bool"
            ShapeType.BYTE -> "Byte"
            ShapeType.SHORT -> "Int16"
            ShapeType.INTEGER -> "Int32"
            ShapeType.LONG -> "Int64"
            ShapeType.BLOB -> "ByteArray"
            ShapeType.STRING -> "String"
            ShapeType.TIMESTAMP -> "Timestamp"
            ShapeType.ENUM -> "String"
            ShapeType.INT_ENUM -> "Int32"
            else -> throw CodegenException("unsupported shape type `${target.type}` for eventHeader member `$member`; target: $target")
        }
        val conversion = when (target.type) {
            ShapeType.BYTE -> ".toUByte()"
            ShapeType.ENUM, ShapeType.INT_ENUM -> ".value"
            else -> ""
        }

        writer.write(
            "input.value.#L?.let { addHeader(#S, #T.#L(it$conversion)) }",
            member.defaultName(),
            member.memberName,
            RuntimeTypes.AwsEventStream.HeaderValue,
            headerValue,
        )
    }

    private fun renderSerializeEventPayload(ctx: ProtocolGenerator.GenerationContext, member: MemberShape, writer: KotlinWriter) {
        // structure > :test(member > :test(blob, string, structure, union))
        val target = ctx.model.expectShape(member.target)
        when (target.type) {
            // input is the sealed class, each variant is generated with `value` as the property name of the type being wrapped
            ShapeType.BLOB -> {
                writer.addStringHeader(":content-type", "application/octet-stream")
                writer.write("payload = input.value.#L", member.defaultName())
            }
            ShapeType.STRING -> {
                writer.addStringHeader(":content-type", "text/plain")
                writer.write("payload = input.value.#L?.#T()", member.defaultName(), KotlinTypes.Text.encodeToByteArray)
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                writer.addStringHeader(":content-type", payloadContentType)
                // re-use the payload serializer
                val serializeFn = sdg.payloadSerializer(ctx, member)
                writer.write("payload = input.value.#L?.let { #T(it) }", member.defaultName(), serializeFn)
            }
            else -> throw CodegenException("unsupported shape type `${target.type}` for target: $target; expected blob, string, structure, or union for eventPayload member: $member")
        }
    }

    private fun KotlinWriter.addStringHeader(name: String, value: String) {
        write("addHeader(#S, #T.String(#S))", name, RuntimeTypes.AwsEventStream.HeaderValue, value)
    }

    /**
     * Get all the shape's members which aren't an event stream
     */
    private fun StructureShape.initialRequestMembers(ctx: ProtocolGenerator.GenerationContext) = members().filter {
        val targetShape = ctx.model.getShape(it.target).getOrNull()
        targetShape?.hasTrait<StreamingTrait>() == false
    }
}
