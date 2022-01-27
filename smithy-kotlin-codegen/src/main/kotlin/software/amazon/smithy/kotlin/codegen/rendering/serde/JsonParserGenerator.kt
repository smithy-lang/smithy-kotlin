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
import software.amazon.smithy.kotlin.codegen.rendering.protocol.filterDocumentBoundMembers
import software.amazon.smithy.kotlin.codegen.rendering.protocol.toRenderingContext
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape

open class JsonParserGenerator(
    private val protocolGenerator: HttpBindingProtocolGenerator
) : StructuredDataParserGenerator {

    override fun operationDeserializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol {
        val outputSymbol = op.output.get().let { ctx.symbolProvider.toSymbol(ctx.model.expectShape(it)) }
        return op.bodyDeserializer(ctx.settings) { writer ->
            addNestedDocumentDeserializers(ctx, op, writer)
            val fnName = op.bodyDeserializerName()
            writer.openBlock("private fun #L(builder: #T.Builder, payload: ByteArray) {", fnName, outputSymbol)
                .call {
                    renderDeserializeOperationBody(ctx, op, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Register nested structure/map shapes reachable from the operation input shape that require a "document" deserializer
     * implementation
     */
    private fun addNestedDocumentDeserializers(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDocumentDeserializer = serdeIndex.requiresDocumentDeserializer(listOf(op))
        // register a dependency on each of the members that require a deserializer impl
        // ensuring they get generated
        shapesRequiringDocumentDeserializer.forEach {
            val nestedStructOrUnionDeserializer = documentDeserializer(ctx, it)
            writer.addImport(nestedStructOrUnionDeserializer)
        }
    }

    private fun renderDeserializeOperationBody(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeJson.JsonDeserializer)
        val resolver = protocolGenerator.getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val responseBindings = resolver.responseBindings(op)
        val documentMembers = responseBindings.filterDocumentBoundMembers()

        val shape = ctx.model.expectShape(op.output.get())

        val httpPayload = responseBindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
        if (httpPayload != null) {
            // explicitly bound member, delegate to the document deserializer
            // val memberSymbol = ctx.symbolProvider.toSymbol(httpPayload.member)
            // writer.write("builder.${httpPayload.member.defaultName()} = #L(deserializer)", memberSymbol.documentDeserializerName())
            TODO("not sure where we trigger this codepath...")
        } else {
            renderDeserializerBody(ctx, shape, documentMembers, writer)
        }
    }

    open fun documentDeserializer(ctx: ProtocolGenerator.GenerationContext, shape: Shape): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        return symbol.documentDeserializer(ctx.settings) { writer ->
            val fnName = symbol.documentDeserializerName()
            writer.openBlock("internal fun #L(deserializer: #T): #T {", fnName, RuntimeTypes.Serde.Deserializer, symbol)
                .call {
                    if (shape.isUnionShape) {
                        writer.write("var value: #T? = null", symbol)
                        renderDeserializerBody(ctx, shape, shape.members().toList(), writer)
                        writer.write("return value ?: throw #T(#S)", RuntimeTypes.Serde.DeserializationException, "Deserialized union value unexpectedly null: ${symbol.name}")
                    } else {
                        writer.write("val builder = #T.Builder()", symbol)
                        renderDeserializerBody(ctx, shape, shape.members().toList(), writer)
                        writer.write("return builder.build()")
                    }
                }
                .closeBlock("}")
        }
    }

    override fun errorDeserializer(ctx: ProtocolGenerator.GenerationContext, errorShape: StructureShape): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(errorShape)
        return symbol.errorDeserializer(ctx.settings) { writer ->
            val fnName = symbol.errorDeserializerName()
            writer.openBlock("internal fun #L(builder: #T.Builder, payload: ByteArray) {", fnName, symbol)
                .call {
                    val resolver = protocolGenerator.getProtocolHttpBindingResolver(ctx.model, ctx.service)
                    val responseBindings = resolver.responseBindings(errorShape)
                    val documentMembers = responseBindings.filterDocumentBoundMembers()
                    writer.write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeJson.JsonDeserializer)
                    renderDeserializerBody(ctx, errorShape, documentMembers, writer)
                }
                .closeBlock("}")
        }
    }

    private fun renderDeserializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        JsonSerdeDescriptorGenerator(ctx.toRenderingContext(protocolGenerator, shape, writer), members).render()
        if (shape.isUnionShape) {
            val name = ctx.symbolProvider.toSymbol(shape).name
            DeserializeUnionGenerator(ctx, name, members, writer, protocolGenerator.defaultTimestampFormat).render()
        } else {
            DeserializeStructGenerator(ctx, members, writer, protocolGenerator.defaultTimestampFormat).render()
        }
    }
}
