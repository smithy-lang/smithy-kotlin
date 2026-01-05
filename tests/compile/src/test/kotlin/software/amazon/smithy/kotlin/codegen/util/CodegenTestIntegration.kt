/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.util

import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.lang.KotlinTypes
import aws.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.HttpBindingResolver
import aws.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.HttpTraitResolver
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolContentTypes
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import aws.smithy.kotlin.codegen.rendering.protocol.errorHandler
import aws.smithy.kotlin.codegen.rendering.protocol.errorHandlerName
import aws.smithy.kotlin.codegen.rendering.serde.JsonParserGenerator
import aws.smithy.kotlin.codegen.rendering.serde.JsonSerializerGenerator
import aws.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import aws.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import aws.smithy.kotlin.codegen.rendering.serde.XmlParserGenerator
import aws.smithy.kotlin.codegen.rendering.serde.XmlSerializerGenerator
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.aws.traits.protocols.RestXmlTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Integration that registers protocol generators this package provides
 */
class CodegenTestIntegration : KotlinIntegration {
    override val protocolGenerators: List<ProtocolGenerator> = listOf(
        RestJsonTestProtocolGenerator(),
        RestXmlTestProtocolGenerator(),
    )
}

/**
 * A partial ProtocolGenerator to generate minimal sdks for tests of restJson models.
 *
 * This class copies some sdk field and object descriptor generation from aws-sdk-kotlin in order
 * to produce code that compiles, but lacks sufficient protocol metadata to successfully drive
 * serde for any protocol.
 */
class RestJsonTestProtocolGenerator(
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS,
    override val protocol: ShapeId = RestJson1Trait.ID,
) : HttpBindingProtocolGenerator() {

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        HttpTraitResolver(model, serviceShape, ProtocolContentTypes.consistent("application/json"))

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {
        // NOP
    }

    override fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator =
        MockRestJsonProtocolClientGenerator(ctx, getHttpMiddleware(ctx), getProtocolHttpBindingResolver(ctx.model, ctx.service))

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        JsonSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        JsonParserGenerator(this)

    override fun operationErrorHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol =
        op.errorHandler(ctx.settings) { writer ->
            writer.withBlock(
                "private fun ${op.errorHandlerName()}(context: #T, call: #T, payload: #T?): Nothing {",
                "}",
                RuntimeTypes.Core.ExecutionContext,
                RuntimeTypes.Http.HttpCall,
                KotlinTypes.ByteArray,
            ) {
                write("error(\"not needed for compile tests\")")
            }
        }
}

class MockRestJsonProtocolClientGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    middleware: List<ProtocolMiddleware>,
    httpBindingResolver: HttpBindingResolver,
) : HttpProtocolClientGenerator(ctx, middleware, httpBindingResolver)

/**
 * A partial ProtocolGenerator to generate minimal sdks for tests of restXml models.
 */
class RestXmlTestProtocolGenerator(
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS,
    override val protocol: ShapeId = RestXmlTrait.ID,
) : HttpBindingProtocolGenerator() {

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        HttpTraitResolver(model, serviceShape, ProtocolContentTypes.consistent("application/xml"))

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {
        // NOOP
    }

    override fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator =
        MockRestXmlProtocolClientGenerator(ctx, getHttpMiddleware(ctx), getProtocolHttpBindingResolver(ctx.model, ctx.service))

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        XmlSerializerGenerator(this, TimestampFormatTrait.Format.EPOCH_SECONDS)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        XmlParserGenerator(TimestampFormatTrait.Format.EPOCH_SECONDS)

    override fun operationErrorHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol =
        op.errorHandler(ctx.settings) { writer ->
            writer.withBlock(
                "private fun ${op.errorHandlerName()}(context: #T, call: #T, payload: #T?): Nothing {",
                "}",
                RuntimeTypes.Core.ExecutionContext,
                RuntimeTypes.Http.HttpCall,
                KotlinTypes.ByteArray,
            ) {
                write("error(\"not needed for compile tests\")")
            }
        }
}

class MockRestXmlProtocolClientGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    middleware: List<ProtocolMiddleware>,
    httpBindingResolver: HttpBindingResolver,
) : HttpProtocolClientGenerator(ctx, middleware, httpBindingResolver)
