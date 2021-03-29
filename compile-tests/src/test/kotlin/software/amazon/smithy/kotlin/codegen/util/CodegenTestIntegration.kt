/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.util

import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinDependency
import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.buildSymbol
import software.amazon.smithy.kotlin.codegen.integration.*
import software.amazon.smithy.kotlin.codegen.namespace
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Integration that registers protocol generators this package provides
 */
class CodegenTestIntegration : KotlinIntegration {
    override val protocolGenerators: List<ProtocolGenerator> = listOf(RestJsonTestProtocolGenerator())
}

/**
 * A partial ProtocolGenerator to generate minimal sdks for tests of restJson models.
 */
class RestJsonTestProtocolGenerator(
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS,
    override val protocol: ShapeId = RestJson1Trait.ID
) : HttpBindingProtocolGenerator() {

    override fun getProtocolHttpBindingResolver(ctx: ProtocolGenerator.GenerationContext): HttpBindingResolver = HttpTraitResolver(ctx, "application/json")

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {
        // NOP
    }

    override fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator {
        return MockRestJsonProtocolClientGenerator(ctx, getHttpFeatures(ctx), getProtocolHttpBindingResolver(ctx))
    }
}

class MockRestJsonProtocolClientGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    features: List<HttpFeature>,
    httpBindingResolver: HttpBindingResolver
) : HttpProtocolClientGenerator(ctx, features, httpBindingResolver) {

    override val serdeProviderSymbol: Symbol
        get() = buildSymbol {
            name = "JsonSerdeProvider"
            namespace(KotlinDependency.CLIENT_RT_SERDE_JSON)
        }
}