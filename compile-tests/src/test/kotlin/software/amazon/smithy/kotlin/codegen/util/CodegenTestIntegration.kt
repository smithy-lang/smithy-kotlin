/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.util

import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.namespace
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
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
 *
 * This class copies some sdk field and object descriptor generation from aws-sdk-kotlin in order
 * to produce code that compiles, but lacks sufficient protocol metadata to successfully drive
 * serde for any protocol.
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
        return MockRestJsonProtocolClientGenerator(ctx, getHttpMiddleware(ctx), getProtocolHttpBindingResolver(ctx))
    }

    override fun renderSerializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter
    ) {
        writer.write("TODO(\"not-implemented - compile only test\")")
    }

    override fun renderDeserializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter
    ) {
        writer.write("TODO(\"not-implemented - compile only test\")")
    }

    override fun renderSerializeDocumentBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        writer: KotlinWriter
    ) {
        writer.write("TODO(\"not-implemented - compile only test\")")
    }

    override fun renderDeserializeDocumentBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        writer: KotlinWriter
    ) {
        writer.write("TODO(\"not-implemented - compile only test\")")
    }

    override fun renderDeserializeException(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        writer: KotlinWriter
    ) {
        writer.write("TODO(\"not-implemented - compile only test\")")
    }

}

class MockRestJsonProtocolClientGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    middleware: List<ProtocolMiddleware>,
    httpBindingResolver: HttpBindingResolver
) : HttpProtocolClientGenerator(ctx, middleware, httpBindingResolver) {

}
