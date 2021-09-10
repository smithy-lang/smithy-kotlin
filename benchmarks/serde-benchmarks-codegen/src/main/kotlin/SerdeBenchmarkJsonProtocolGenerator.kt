/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.protocols

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
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
    override fun renderSerializeOperationBody(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) { }
    override fun renderDeserializeOperationBody(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) { }
    override fun renderDeserializeException(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter) { }
    override fun renderThrowOperationError(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) { }

    override fun renderSerializeDocumentBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        writer: KotlinWriter
    ) {
        val members = shape.members().toList()
        // render the serde descriptors
        JsonSerdeDescriptorGenerator(ctx.toRenderingContext(this, shape, writer), members).render()
        if (shape.isUnionShape) {
            SerializeUnionGenerator(ctx, members, writer, defaultTimestampFormat).render()
        } else {
            SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat).render()
        }
    }

    override fun renderDeserializeDocumentBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        writer: KotlinWriter
    ) {
        val members = shape.members().toList()
        JsonSerdeDescriptorGenerator(ctx.toRenderingContext(this, shape, writer), members).render()
        if (shape.isUnionShape) {
            val name = ctx.symbolProvider.toSymbol(shape).name
            DeserializeUnionGenerator(ctx, name, members, writer, defaultTimestampFormat).render()
        } else {
            DeserializeStructGenerator(ctx, members, writer, defaultTimestampFormat).render()
        }
    }
}
