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

class CborSerializerGenerator(
    private val protocolGenerator: ProtocolGenerator,
) : StructuredDataSerializerGenerator {
    override fun operationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol {
        val serializationTarget = if (ctx.settings.build.generateServiceProject) {
            op.output
        } else {
            op.input
        }
        val shape = serializationTarget.get().let { ctx.model.expectShape(it) }
        val symbol = ctx.symbolProvider.toSymbol(shape)

        return op.bodySerializer(ctx.settings) { writer ->
            addNestedDocumentSerializers(ctx, op, writer)
            writer.withBlock("private fun #L(context: #T, input: #T): #T {", "}", op.bodySerializerName(), RuntimeTypes.Core.ExecutionContext, symbol, RuntimeTypes.Http.HttpBody) {
                call {
                    renderSerializeOperationBody(ctx, op, members, writer)
                }
            }
        }
    }

    private fun renderSerializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        documentMembers: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        val serializationTarget = if (ctx.settings.build.generateServiceProject) {
            op.output
        } else {
            op.input
        }
        val shape = ctx.model.expectShape(serializationTarget.get())
        writer.write("val serializer = #T()", RuntimeTypes.Serde.SerdeCbor.CborSerializer)
        renderSerializerBody(ctx, shape, documentMembers, writer)
        writer.write("return serializer.toHttpBody()")
    }

    private fun renderSerializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        descriptorGenerator(ctx, shape, members, writer).render()
        when (shape) {
            is DocumentShape -> writer.write("serializer.serializeDocument(input)")
            is UnionShape -> SerializeUnionGenerator(ctx, shape, members, writer, TimestampFormatTrait.Format.EPOCH_SECONDS).render()
            else -> SerializeStructGenerator(ctx, members, writer, TimestampFormatTrait.Format.EPOCH_SECONDS).render()
        }
    }

    private fun descriptorGenerator(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) = CborSerdeDescriptorGenerator(ctx.toRenderingContext(protocolGenerator, shape, writer), members)

    override fun payloadSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape>?,
    ): Symbol {
        val target = shape.targetOrSelf(ctx.model)
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val forMembers = members ?: target.members()

        val serializeFn = documentSerializer(ctx, shape, forMembers)

        return target.payloadSerializer(ctx.settings, symbol, forMembers) { writer ->
            addNestedDocumentSerializers(ctx, target, writer)
            writer.addImportReferences(symbol, SymbolReference.ContextOption.USE)
            writer.withBlock("internal fun #identifier.name:L(input: #T): ByteArray {", "}", symbol) {
                write("val serializer = #T()", RuntimeTypes.Serde.SerdeCbor.CborSerializer)
                write("#T(serializer, input)", serializeFn)
                write("return serializer.toByteArray()")
            }
        }
    }

    /**
     * Register nested structure/map shapes reachable from the operation input shape that require a "document" serializer
     * implementation
     */
    private fun addNestedDocumentSerializers(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter) {
        SerdeIndex.of(ctx.model)
            .requiresDocumentSerializer(shape)
            .forEach {
                writer.addImport(documentSerializer(ctx, it))
            }
    }

    private fun documentSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape> = shape.members(),
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        return shape.documentSerializer(ctx.settings, symbol, members) { writer ->
            writer.withBlock("internal fun #identifier.name:L(serializer: #T, input: #T) {", "}", RuntimeTypes.Serde.Serializer, symbol) {
                call { renderSerializerBody(ctx, shape, members.toList(), writer) }
            }
        }
    }
}
