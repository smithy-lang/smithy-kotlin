/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.toRenderingContext
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.TimestampFormatTrait

open class JsonSerializerGenerator(
    // FIXME - we shouldn't need this, it's only required by JsonSerdeDescriptorGenerator because of toRenderingContext
    private val protocolGenerator: HttpBindingProtocolGenerator
) : StructuredDataSerializeGenerator {

    open val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS

    override fun operationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol {
        val input = op.input.get().let { ctx.model.expectShape(it) }
        val symbol = ctx.symbolProvider.toSymbol(input)

        return op.bodySerializer(ctx.settings) { writer ->
            addNestedDocumentSerializers(ctx, op, writer)
            val fnName = op.bodySerializerName()
            writer.openBlock("private fun #L(context: #T, input: #T): ByteArray {", fnName, RuntimeTypes.Core.ExecutionContext, symbol)
                .call {
                    renderSerializeOperationBody(ctx, op, members, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Register nested structure/map shapes reachable from the operation input shape that require a "document" serializer
     * implementation
     */
    private fun addNestedDocumentSerializers(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDocumentSerializer = serdeIndex.requiresDocumentSerializer(listOf(op))
        // register a dependency on each of the members that require a serializer impl
        // ensuring they get generated
        shapesRequiringDocumentSerializer.forEach {
            val nestedStructOrUnionSerializer = documentSerializer(ctx, it)
            writer.addImport(nestedStructOrUnionSerializer)
        }
    }

    private fun renderSerializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        documentMembers: List<MemberShape>,
        writer: KotlinWriter
    ) {
        // val resolver = protocolGenerator.getProtocolHttpBindingResolver(ctx.model, ctx.service)
        // val requestBindings = resolver.requestBindings(op)
        // val documentMembers = requestBindings.filterDocumentBoundMembers()

        val shape = ctx.model.expectShape(op.input.get())
        writer.write("val serializer = #T()", RuntimeTypes.Serde.SerdeJson.JsonSerializer)

        // restJson protocol supports the httpPayload trait
        // val httpPayload = requestBindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
        // if (httpPayload != null) {
        //     TODO("not sure when or where we hit this codepath...")
        // } else {
        //     renderSerializerBody(ctx, shape, documentMembers, writer)
        // }

        renderSerializerBody(ctx, shape, documentMembers, writer)
        writer.write("return serializer.toByteArray()")
    }

    open fun documentSerializer(ctx: ProtocolGenerator.GenerationContext, shape: Shape): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        return symbol.documentSerializer(ctx.settings) { writer ->
            val fnName = symbol.documentSerializerName()
            writer.openBlock("internal fun #L(serializer: #T, input: #T){", fnName, RuntimeTypes.Serde.Serializer, symbol)
                .call {
                    renderSerializerBody(ctx, shape, shape.members().toList(), writer)
                }
                .closeBlock("}")
        }
    }

    private fun renderSerializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        // render the serde descriptors
        JsonSerdeDescriptorGenerator(ctx.toRenderingContext(protocolGenerator, shape, writer), members).render()
        if (shape.isUnionShape) {
            SerializeUnionGenerator(ctx, members, writer, defaultTimestampFormat).render()
        } else {
            SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat).render()
        }
    }
}
