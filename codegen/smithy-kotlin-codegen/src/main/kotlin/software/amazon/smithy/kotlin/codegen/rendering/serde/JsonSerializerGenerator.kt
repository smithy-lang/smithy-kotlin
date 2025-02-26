/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import software.amazon.smithy.kotlin.codegen.model.targetOrSelf
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.toRenderingContext
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

open class JsonSerializerGenerator(
    // FIXME - we shouldn't need this, it's only required by JsonSerdeDescriptorGenerator because of toRenderingContext
    private val protocolGenerator: ProtocolGenerator,
    private val supportsJsonNameTrait: Boolean = true,
) : StructuredDataSerializerGenerator {

    open val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS

    override fun operationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol {
        val input = ctx.model.expectShape(op.input.get())
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
    private fun addNestedDocumentSerializers(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter, members: Collection<MemberShape> = shape.members()) {
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDocumentSerializer = serdeIndex.requiresDocumentSerializer(shape, members)
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
        writer: KotlinWriter,
    ) {
        val shape = ctx.model.expectShape(op.input.get())
        writer.write("val serializer = #T()", RuntimeTypes.Serde.SerdeJson.JsonSerializer)
        renderSerializerBody(ctx, shape, documentMembers, writer)
        writer.write("return serializer.toByteArray()")
    }

    private fun documentSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape> = shape.members(),
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)

        return shape.documentSerializer(ctx.settings, symbol, members) { writer ->
            writer.openBlock("internal fun #identifier.name:L(serializer: #T, input: #T) {", RuntimeTypes.Serde.Serializer, symbol)
                .call {
                    renderSerializerBody(ctx, shape, members.toList(), writer)
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
        JsonSerdeDescriptorGenerator(ctx.toRenderingContext(protocolGenerator, shape, writer), members, supportsJsonNameTrait).render()
        when (shape) {
            is DocumentShape -> writer.write("serializer.serializeDocument(input)")
            is UnionShape -> SerializeUnionGenerator(ctx, shape, members, writer, defaultTimestampFormat).render()
            else -> SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat).render()
        }
    }

    override fun payloadSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape>?,
    ): Symbol {
        val target = shape.targetOrSelf(ctx.model)
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val forMembers = members ?: target.members()

        val serializeFn = documentSerializer(ctx, target, forMembers)
        return target.payloadSerializer(ctx.settings, symbol, forMembers) { writer ->
            addNestedDocumentSerializers(ctx, target, writer, forMembers)
            writer.addImportReferences(symbol, SymbolReference.ContextOption.USE)
            writer.withBlock("internal fun #identifier.name:L(input: #T): ByteArray {", "}", symbol) {
                write("val serializer = #T()", RuntimeTypes.Serde.SerdeJson.JsonSerializer)
                write("#T(serializer, input)", serializeFn)
                write("return serializer.toByteArray()")
            }
        }
    }
}
