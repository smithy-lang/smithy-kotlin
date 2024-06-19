/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.aws.protocols

import software.amazon.smithy.kotlin.codegen.aws.protocols.core.AwsHttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.aws.protocols.core.StaticHttpBindingResolver
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.model.traits.SyntheticClone
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.CborParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.CborSerializerGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.pattern.UriPattern
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.UnitTypeTrait
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait

class Rpcv2Cbor : AwsHttpBindingProtocolGenerator() {
    override val protocol: ShapeId = Rpcv2CborTrait.ID
    override val defaultTimestampFormat = TimestampFormatTrait.Format.UNKNOWN // not used in Rpcv2Cbor

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        Rpcv2CborHttpBindingResolver(model, serviceShape)

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        CborSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        CborParserGenerator(this)

    override fun renderDeserializeErrorDetails(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.write("#T.deserialize(payload)", RuntimeTypes.SmithyRpcv2Protocols.Cbor.Rpcv2CborErrorDeserializer)
    }

    override fun getDefaultHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> {
        // Every request for the rpcv2Cbor protocol MUST contain a `smithy-protocol` header with the value of `rpc-v2-cbor`
        val smithyProtocolHeaderMiddleware = MutateHeadersMiddleware(overrideHeaders = mapOf("smithy-protocol" to "rpc-v2-cbor"))

        // Requests with event stream responses for the rpcv2Cbor protocol MUST include an `Accept` header set to the value `application/vnd.amazon.eventstream`
        val eventStreamsAcceptHeaderMiddleware = object : ProtocolMiddleware {
            private val mutateHeadersMiddleware = MutateHeadersMiddleware(extraHeaders = mapOf("Accept" to "application/vnd.amazon.eventstream"))

            override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean = op.isOutputEventStream(ctx.model)
            override val name: String = "Rpcv2CborEventStreamsAcceptHeaderMiddleware"
            override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) = mutateHeadersMiddleware.render(ctx, op, writer)
        }

        return super.getDefaultHttpMiddleware(ctx) + listOf(smithyProtocolHeaderMiddleware, eventStreamsAcceptHeaderMiddleware)
    }

    /**
     * Exact copy of [AwsHttpBindingProtocolGenerator.renderSerializeHttpBody] but with a custom
     * [OperationShape.hasHttpBody] function to handle protocol-specific serialization rules.
     */
    override fun renderSerializeHttpBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        if (!op.hasHttpBody(ctx)) return

        // payload member(s)
        val requestBindings = resolver.requestBindings(op)
        val httpPayload = requestBindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
        if (httpPayload != null) {
            renderExplicitHttpPayloadSerializer(ctx, httpPayload, writer)
        } else {
            val documentMembers = requestBindings.filterDocumentBoundMembers()
            // Unbound document members that should be serialized into the document format for the protocol.
            // delegate to the generate operation body serializer function
            val sdg = structuredDataSerializer(ctx)
            val opBodySerializerFn = sdg.operationSerializer(ctx, op, documentMembers)
            writer.write("val payload = #T(context, input)", opBodySerializerFn)
            writer.write("builder.body = #T.fromBytes(payload)", RuntimeTypes.Http.HttpBody)
        }
        renderContentTypeHeader(ctx, op, writer, resolver)
    }

    /**
     * @return whether the operation input does _not_ target the unit shape ([UnitTypeTrait.UNIT])
     */
    private fun OperationShape.hasHttpBody(ctx: ProtocolGenerator.GenerationContext): Boolean {
        val input = ctx.model.expectShape(inputShape).targetOrSelf(ctx.model).let {
            // If the input has been synthetically cloned from the original (most likely),
            // pull the archetype and check _that_
            it.getTrait<SyntheticClone>()?.let { clone ->
                ctx.model.expectShape(clone.archetype).targetOrSelf(ctx.model)
            } ?: it
        }

        return input.id != UnitTypeTrait.UNIT
    }

    override fun renderContentTypeHeader(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
        resolver: HttpBindingResolver,
    ) {
        writer.write("builder.headers.setMissing(\"Content-Type\", #S)", resolver.determineRequestContentType(op))
    }

    class Rpcv2CborHttpBindingResolver(
        model: Model,
        val serviceShape: ServiceShape,
    ) : StaticHttpBindingResolver(
        model,
        serviceShape,
        HttpTrait.builder().code(200).method("POST").uri(UriPattern.parse("/")).build(),
        "application/cbor",
        TimestampFormatTrait.Format.EPOCH_SECONDS,
    ) {

        override fun httpTrait(operationShape: OperationShape): HttpTrait = HttpTrait
            .builder()
            .code(200)
            .method("POST")
            .uri(UriPattern.parse("/service/${serviceShape.id.name}/operation/${operationShape.id.name}"))
            .build()

        override fun determineRequestContentType(operationShape: OperationShape): String = when {
            operationShape.isInputEventStream(model) -> "application/vnd.amazon.eventstream"
            else -> "application/cbor"
        }
    }
}
