/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.protocols

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Protocol generator for benchmark protocol [SerdeBenchmarkJsonProtocol]
 */
class SerdeBenchmarkJsonProtocolGenerator : HttpBindingProtocolGenerator() {
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS
    override val protocol: ShapeId = SerdeBenchmarkJsonProtocol.ID

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        HttpTraitResolver(model, serviceShape, "application/json")

    override fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator =
        object : HttpProtocolClientGenerator(ctx, emptyList(), getProtocolHttpBindingResolver(ctx.model, ctx.service)) {}

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) { }


    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializeGenerator =
        JsonSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        JsonParserGenerator(this)

    override fun operationErrorHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol =
        op.errorHandler(ctx.settings) { writer ->
            writer.withBlock(
                "private suspend fun ${op.errorHandlerName()}(context: #T, response: #T): Nothing",
                "}",
                RuntimeTypes.Core.ExecutionContext,
                RuntimeTypes.Http.Response.HttpResponse
            ) {
                write("error(\"not needed for benchmark tests\")")
            }
        }
}
