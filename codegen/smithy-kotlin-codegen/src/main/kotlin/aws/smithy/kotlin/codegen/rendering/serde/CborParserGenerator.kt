/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import aws.smithy.kotlin.codegen.model.targetOrSelf
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.toRenderingContext
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

class CborParserGenerator(
    private val protocolGenerator: ProtocolGenerator,
) : StructuredDataParserGenerator {

    override fun operationDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        members: List<MemberShape>,
    ): Symbol {
        val outputSymbol = ctx.symbolProvider.toSymbol(ctx.model.expectShape(op.outputShape))

        return op.bodyDeserializer(ctx.settings) { writer ->
            addNestedDocumentDeserializers(ctx, op, writer)
            val fnName = op.bodyDeserializerName()
            writer.withBlock("private fun #L(builder: #T.Builder, payload: ByteArray) {", "}", fnName, outputSymbol) {
                call { renderDeserializeOperationBody(ctx, op, members, writer) }
            }
        }
    }

    /**
     * Register nested structure/map shapes reachable from the operation input shape that require a "document" deserializer
     * implementation
     * @param ctx the generation context
     * @param shape the shape to generated nested document deserializers for
     * @param writer the writer to write with
     * @param members the subset of shapes to generated nested document deserializers for
     */
    private fun addNestedDocumentDeserializers(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter, members: Collection<MemberShape> = shape.members()) {
        SerdeIndex.Companion.of(ctx.model)
            .requiresDocumentDeserializer(shape, members)
            .forEach {
                writer.addImport(documentDeserializer(ctx, it))
            }
    }

    private fun documentDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape> = shape.members(),
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)

        return shape.documentDeserializer(ctx.settings, symbol, members) { writer ->
            writer.withBlock("internal fun #identifier.name:L(deserializer: #T): #T {", "}", RuntimeTypes.Serde.SerdeCbor.CborDeserializer, symbol) {
                call {
                    when (shape.type) {
                        ShapeType.DOCUMENT -> writer.write("return deserializer.deserializeDocument()")
                        ShapeType.UNION -> {
                            writer.write("var value: #T? = null", symbol)
                            renderDeserializerBody(ctx, shape, members.toList(), writer)
                            writer.write(
                                "return value ?: throw #T(#S)",
                                RuntimeTypes.Serde.DeserializationException,
                                "Deserialized union value unexpectedly null: ${symbol.name}",
                            )
                        }
                        else -> {
                            writer.write("val builder = #T.Builder()", symbol)
                            renderDeserializerBody(ctx, shape, members.toList(), writer)
                            writer.write("builder.correctErrors()")
                            writer.write("return builder.build()")
                        }
                    }
                }
            }
        }
    }

    private fun renderDeserializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        documentMembers: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        writer.write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeCbor.CborDeserializer)

        val shape = ctx.model.expectShape(op.output.get())
        renderDeserializerBody(ctx, shape, documentMembers, writer)
    }

    private fun renderDeserializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        descriptorGenerator(ctx, shape, members, writer).render()

        if (shape.isUnionShape) {
            val name = ctx.symbolProvider.toSymbol(shape).name
            DeserializeUnionGenerator(ctx, name, members, writer, TimestampFormatTrait.Format.EPOCH_SECONDS).render()
        } else {
            DeserializeStructGenerator(ctx, members, writer, TimestampFormatTrait.Format.EPOCH_SECONDS).render()
        }
    }

    private fun descriptorGenerator(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) = CborSerdeDescriptorGenerator(ctx.toRenderingContext(protocolGenerator, shape, writer), members)

    override fun payloadDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape>?,
    ): Symbol {
        val target = shape.targetOrSelf(ctx.model)
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val forMembers = members ?: target.members()
        val deserializeFn = documentDeserializer(ctx, target, forMembers)
        return target.payloadDeserializer(ctx.settings, symbol, forMembers) { writer ->
            addNestedDocumentDeserializers(ctx, target, writer, forMembers)
            writer.withBlock("internal fun #identifier.name:L(payload: ByteArray): #T {", "}", symbol) {
                if (target.members().isEmpty() && !target.isDocumentShape) {
                    // short circuit when the shape has no modeled members to deserialize
                    write("return #T.Builder().build()", symbol)
                } else {
                    write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeCbor.CborDeserializer)
                    write("return #T(deserializer)", deserializeFn)
                }
            }
        }
    }

    override fun errorDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        errorShape: StructureShape,
        members: List<MemberShape>,
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(errorShape)

        return symbol.errorDeserializer(ctx.settings) { writer ->
            addNestedDocumentDeserializers(ctx, errorShape, writer)
            val fnName = symbol.errorDeserializerName()
            writer.withBlock("private fun #L(builder: #T.Builder, payload: ByteArray) {", "}", fnName, symbol) {
                writer.write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeCbor.CborDeserializer)
                call { renderDeserializerBody(ctx, errorShape, members, writer) }
            }
        }
    }
}
