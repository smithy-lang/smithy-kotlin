/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.toRenderingContext
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.XmlAttributeTrait

/**
 * XML serializer generator based on common serializer interface and XML serde descriptors
 */
open class XmlSerializerGenerator(
    private val protocolGenerator: ProtocolGenerator,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) : StructuredDataSerializerGenerator {

    open fun descriptorGenerator(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter
    ): XmlSerdeDescriptorGenerator = XmlSerdeDescriptorGenerator(ctx.toRenderingContext(protocolGenerator, shape, writer), members)

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
    protected fun addNestedDocumentSerializers(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter) {
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDocumentSerializer = serdeIndex.requiresDocumentSerializer(shape)
        // register a dependency on each of the members that require a serializer impl
        // ensuring they get generated
        shapesRequiringDocumentSerializer.forEach {
            val nestedStructOrUnionSerializer = documentSerializer(ctx, it)
            writer.addImport(nestedStructOrUnionSerializer)
        }
    }

    protected open fun renderSerializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        documentMembers: List<MemberShape>,
        writer: KotlinWriter
    ) {
        val shape = ctx.model.expectShape(op.input.get())
        writer.write("val serializer = #T()", RuntimeTypes.Serde.SerdeXml.XmlSerializer)
        renderSerializerBody(ctx, shape, documentMembers, writer)
        writer.write("return serializer.toByteArray()")
    }

    protected fun documentSerializer(ctx: ProtocolGenerator.GenerationContext, shape: Shape): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        return symbol.documentSerializer(ctx.settings) { writer ->
            val fnName = symbol.documentSerializerName()
            writer.openBlock("internal fun #L(serializer: #T, input: #T) {", fnName, RuntimeTypes.Serde.Serializer, symbol)
                .call {
                    renderSerializerBody(ctx, shape, shape.members().toList(), writer)
                }
                .closeBlock("}")
        }
    }

    protected fun renderSerializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        // order is important due to attributes
        val sortedMembers = sortMembersForSerialization(members)
        descriptorGenerator(ctx, shape, sortedMembers, writer).render()
        // render the serde descriptors
        if (shape.isUnionShape) {
            SerializeUnionGenerator(ctx, sortedMembers, writer, defaultTimestampFormat).render()
        } else {
            SerializeStructGenerator(ctx, sortedMembers, writer, defaultTimestampFormat).render()
        }
    }

    private fun sortMembersForSerialization(
        members: List<MemberShape>
    ): List<MemberShape> {
        val attributes = members.filter { it.hasTrait<XmlAttributeTrait>() }.sortedBy { it.memberName }
        val elements = members.filterNot { it.hasTrait<XmlAttributeTrait>() }.sortedBy { it.memberName }

        // XML attributes MUST be serialized immediately following calls to `startTag` before
        // any nested content is serialized
        return attributes + elements
    }

    override fun payloadSerializer(ctx: ProtocolGenerator.GenerationContext, member: MemberShape): Symbol {
        // re-use document serializer
        val symbol = ctx.symbolProvider.toSymbol(member)
        val target = ctx.model.expectShape(member.target)
        val serializeFn = documentSerializer(ctx, target)
        val fnName = symbol.payloadSerializerName()
        return symbol.payloadSerializer(ctx.settings) { writer ->
            addNestedDocumentSerializers(ctx, target, writer)
            writer.addImportReferences(symbol, SymbolReference.ContextOption.USE)
            writer.withBlock("internal fun #L(input: #T): ByteArray {", "}", fnName, symbol) {
                write("val serializer = #T()", RuntimeTypes.Serde.SerdeXml.XmlSerializer)
                write("#T(serializer, input)", serializeFn)
                write("return serializer.toByteArray()")
            }
        }
    }
}
