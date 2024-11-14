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

private const val ACCEPT_HEADER = "application/cbor"
private const val ACCEPT_HEADER_EVENT_STREAM = "application/vnd.amazon.eventstream"

class RpcV2Cbor : AwsHttpBindingProtocolGenerator() {
    override val protocol: ShapeId = Rpcv2CborTrait.ID

    // TODO Timestamp format is not used in RpcV2Cbor since it's a binary protocol. We seem to be missing an abstraction
    // between text-based and binary-based protocols
    override val defaultTimestampFormat = TimestampFormatTrait.Format.UNKNOWN

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        RpcV2CborHttpBindingResolver(model, serviceShape)

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        CborSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        CborParserGenerator(this)

    override fun renderDeserializeErrorDetails(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.write("#T.deserialize(payload)", RuntimeTypes.SmithyRpcV2Protocols.Cbor.RpcV2CborErrorDeserializer)
    }

    override fun getDefaultHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> {
        // Every request MUST contain a `smithy-protocol` header with the value of `rpc-v2-cbor`
        val smithyProtocolHeaderMiddleware = MutateHeadersMiddleware(overrideHeaders = mapOf("smithy-protocol" to "rpc-v2-cbor"))

        // Every response MUST contain the same `smithy-protocol` header, otherwise it's considered invalid
        val validateSmithyProtocolHeaderMiddleware = object : ProtocolMiddleware {
            override val name: String = "RpcV2CborValidateSmithyProtocolResponseHeader"
            override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
                val interceptorSymbol = RuntimeTypes.SmithyRpcV2Protocols.Cbor.RpcV2CborSmithyProtocolResponseHeaderInterceptor
                writer.write("op.interceptors.add(#T)", interceptorSymbol)
            }
        }

        // Add `Accept` header with value `application/cbor` for standard responses
        // and `application/vnd.amazon.eventstream` for event stream responses
        val acceptHeaderMiddleware = object : ProtocolMiddleware {
            override val name: String = "RpcV2CborAcceptHeaderMiddleware"
            override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
                val acceptHeaderValue = if (op.isOutputEventStream(ctx.model)) ACCEPT_HEADER_EVENT_STREAM else ACCEPT_HEADER
                MutateHeadersMiddleware(
                    extraHeaders = mapOf("Accept" to acceptHeaderValue),
                ).render(ctx, op, writer)
            }
        }

        // Emit a metric to track usage of RpcV2Cbor
        val businessMetricsMiddleware = object : ProtocolMiddleware {
            override val name: String = "RpcV2CborBusinessMetricsMiddleware"
            override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
                writer.write("op.context.#T(#T.PROTOCOL_RPC_V2_CBOR)", RuntimeTypes.Core.BusinessMetrics.emitBusinessMetric, RuntimeTypes.Core.BusinessMetrics.SmithyBusinessMetric)
            }
        }

        return super.getDefaultHttpMiddleware(ctx) + listOf(
            smithyProtocolHeaderMiddleware,
            validateSmithyProtocolHeaderMiddleware,
            acceptHeaderMiddleware,
            businessMetricsMiddleware,
        )
    }

    /**
     * Exact copy of [HttpBindingProtocolGenerator.renderSerializeHttpBody] but with a custom
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
            writer.write("builder.body = #T(context, input)", opBodySerializerFn)
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
        val contentTypeHeader = when {
            op.isInputEventStream(ctx.model) -> "application/vnd.amazon.eventstream"
            else -> "application/cbor"
        }

        writer.write("builder.headers.setMissing(\"Content-Type\", #S)", contentTypeHeader)
    }

    class RpcV2CborHttpBindingResolver(
        model: Model,
        val serviceShape: ServiceShape,
    ) : StaticHttpBindingResolver(
        model,
        serviceShape,
        HttpTrait.builder().code(200).method("POST").uri(UriPattern.parse("/")).build(),
        "application/cbor",
        TimestampFormatTrait.Format.UNKNOWN,
    ) {
        override fun httpTrait(operationShape: OperationShape): HttpTrait = HttpTrait
            .builder()
            .code(200)
            .method("POST")
            .uri(UriPattern.parse("/service/${serviceShape.id.name}/operation/${operationShape.id.name}"))
            .build()

        override fun determineRequestContentType(operationShape: OperationShape): String = "application/cbor"
    }
}
