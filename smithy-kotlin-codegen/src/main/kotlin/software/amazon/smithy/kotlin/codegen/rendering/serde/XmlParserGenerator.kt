/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.toRenderingContext
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * XML parser generator based on common deserializer interface and XML serde descriptors
 */
open class XmlParserGenerator(
    // FIXME - shouldn't be necessary but XML serde descriptor generator needs it for rendering context
    private val protocolGenerator: ProtocolGenerator,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) : StructuredDataParserGenerator {

    open fun descriptorGenerator(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter
    ): XmlSerdeDescriptorGenerator = XmlSerdeDescriptorGenerator(ctx.toRenderingContext(protocolGenerator, shape, writer), members)

    override fun operationDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        members: List<MemberShape>
    ): Symbol {
        val outputSymbol = ctx.symbolProvider.toSymbol(ctx.model.expectShape(op.output.get()))
        return op.bodyDeserializer(ctx.settings) { writer ->
            addNestedDocumentDeserializers(ctx, op, writer)
            val fnName = op.bodyDeserializerName()
            writer.openBlock("private fun #L(builder: #T.Builder, payload: ByteArray) {", fnName, outputSymbol)
                .call {
                    renderDeserializeOperationBody(ctx, op, members, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Register nested structure/map shapes reachable from the operation input shape that require a "document" deserializer
     * implementation
     */
    protected fun addNestedDocumentDeserializers(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter) {
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDocumentDeserializer = serdeIndex.requiresDocumentDeserializer(shape)
        // register a dependency on each of the members that require a deserializer impl
        // ensuring they get generated
        shapesRequiringDocumentDeserializer.forEach {
            val nestedStructOrUnionDeserializer = documentDeserializer(ctx, it)
            writer.addImport(nestedStructOrUnionDeserializer)
        }
    }

    protected open fun renderDeserializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        documentMembers: List<MemberShape>,
        writer: KotlinWriter
    ) {
        writer.write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeXml.XmlDeserializer)
        val shape = ctx.model.expectShape(op.output.get())
        renderDeserializerBody(ctx, shape, documentMembers, writer)
    }

    protected fun renderDeserializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        descriptorGenerator(ctx, shape, members, writer).render()
        if (shape.isUnionShape) {
            val name = ctx.symbolProvider.toSymbol(shape).name
            DeserializeUnionGenerator(ctx, name, members, writer, defaultTimestampFormat).render()
        } else {
            DeserializeStructGenerator(ctx, members, writer, defaultTimestampFormat).render()
        }
    }

    protected fun documentDeserializer(ctx: ProtocolGenerator.GenerationContext, shape: Shape): Symbol {
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

    override fun errorDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        errorShape: StructureShape,
        members: List<MemberShape>
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(errorShape)
        return symbol.errorDeserializer(ctx.settings) { writer ->
            addNestedDocumentDeserializers(ctx, errorShape, writer)
            val fnName = symbol.errorDeserializerName()
            writer.openBlock("internal fun #L(builder: #T.Builder, payload: ByteArray) {", fnName, symbol)
                .call {
                    writer.write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeXml.XmlDeserializer)
                    renderDeserializerBody(ctx, errorShape, members, writer)
                }
                .closeBlock("}")
        }
    }

    override fun payloadDeserializer(ctx: ProtocolGenerator.GenerationContext, member: MemberShape): Symbol {
        // re-use document deserializer
        val symbol = ctx.symbolProvider.toSymbol(member)
        val target = ctx.model.expectShape(member.target)
        val deserializeFn = documentDeserializer(ctx, target)
        val fnName = symbol.payloadDeserializerName()
        return symbol.payloadDeserializer(ctx.settings) { writer ->
            addNestedDocumentDeserializers(ctx, target, writer)
            writer.withBlock("internal fun #L(payload: ByteArray): #T {", "}", fnName, symbol) {
                write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeXml.XmlDeserializer)
                write("return #T(deserializer)", deserializeFn)
            }
        }
    }
}
