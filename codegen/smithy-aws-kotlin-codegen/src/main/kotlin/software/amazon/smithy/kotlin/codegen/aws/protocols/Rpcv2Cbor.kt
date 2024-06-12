/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.aws.protocols

import CborParserGenerator
import software.amazon.smithy.kotlin.codegen.aws.protocols.core.AwsHttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.aws.protocols.core.StaticHttpBindingResolver
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.model.isInputEventStream
import software.amazon.smithy.kotlin.codegen.model.isOutputEventStream
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingResolver
import software.amazon.smithy.kotlin.codegen.rendering.protocol.MutateHeadersMiddleware
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import software.amazon.smithy.kotlin.codegen.rendering.serde.CborSerializerGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.pattern.UriPattern
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait

class Rpcv2Cbor : AwsHttpBindingProtocolGenerator() {
    override val protocol: ShapeId = Rpcv2CborTrait.ID
    override val defaultTimestampFormat = TimestampFormatTrait.Format.EPOCH_SECONDS

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver = Rpcv2CborHttpBindingResolver(model, serviceShape)

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator = CborSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator = CborParserGenerator(this)

    override fun renderDeserializeErrorDetails(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        writer.write("#T.deserialize(payload)", RuntimeTypes.SmithyRpcv2Protocols.Cbor.Rpcv2CborErrorDeserializer)
    }

    override fun getDefaultHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> {
         val smithyProtocolHeader = MutateHeadersMiddleware(overrideHeaders = mapOf("smithy-protocol" to "rpc-v2-cbor"))
        return super.getDefaultHttpMiddleware(ctx) + smithyProtocolHeader
    }


    class Rpcv2CborHttpBindingResolver(model: Model, val serviceShape: ServiceShape) : StaticHttpBindingResolver(model, serviceShape, DefaultRpcv2CborHttpTrait, "application/cbor", TimestampFormatTrait.Format.EPOCH_SECONDS) {
        companion object {
            val DefaultRpcv2CborHttpTrait: HttpTrait = HttpTrait
                .builder()
                .code(200)
                .method("POST")
                .uri(UriPattern.parse("/"))
                .build()
        }

        override fun httpTrait(operationShape: OperationShape): HttpTrait = HttpTrait
            .builder()
            .code(200)
            .method("POST")
            .uri(UriPattern.parse("/service/${serviceShape.id.name}/operation/${operationShape.id.name}"))
            .build()

        override fun determineRequestContentType(operationShape: OperationShape): String =
            if (operationShape.isInputEventStream(model) || operationShape.isOutputEventStream(model)) {
                "application/vnd.amazon.eventstream"
            } else {
                "application/cbor"
            }
    }
}
