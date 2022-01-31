/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.util

import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.SymbolRenderer
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingResolver
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpTraitResolver
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Integration that registers protocol generators this package provides
 */
class CodegenTestIntegration : KotlinIntegration {
    override val protocolGenerators: List<ProtocolGenerator> = listOf(RestJsonTestProtocolGenerator())
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
    override val protocol: ShapeId = RestJson1Trait.ID
) : HttpBindingProtocolGenerator() {

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        HttpTraitResolver(model, serviceShape, "application/json")

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {
        // NOP
    }

    override fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator {
        return MockRestJsonProtocolClientGenerator(ctx, getHttpMiddleware(ctx), getProtocolHttpBindingResolver(ctx.model, ctx.service))
    }

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializeGenerator =
        JsonSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        JsonParserGenerator(this)

    override fun renderThrowOperationError(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter
    ) {
        writer.write("""TODO("not-implemented - compile only test")""")
    }

}

class MockRestJsonProtocolClientGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    middleware: List<ProtocolMiddleware>,
    httpBindingResolver: HttpBindingResolver
) : HttpProtocolClientGenerator(ctx, middleware, httpBindingResolver) {

}
