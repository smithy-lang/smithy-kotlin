/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.isStringEnumShape
import software.amazon.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import software.amazon.smithy.kotlin.codegen.model.targetOrSelf
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

open class CborParserGenerator : StructuredDataParserGenerator {
    override fun operationDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        members: List<MemberShape>,
    ): Symbol {
        val outputSymbol = op.output.get().let { ctx.symbolProvider.toSymbol(ctx.model.expectShape(it)) }
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
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDocumentDeserializer = serdeIndex.requiresDocumentDeserializer(shape, members)

        // register a dependency on each of the members that require a deserializer impl
        // ensuring they get generated
        shapesRequiringDocumentDeserializer.forEach {
            val nestedStructOrUnionDeserializer = documentDeserializer(ctx, it)
            writer.addImport(nestedStructOrUnionDeserializer)
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
                        ShapeType.DOCUMENT -> writer.write("return deserializer.deserializeDocument()") // FIXME need to support documents?
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
        if (shape.isUnionShape) {
            val name = ctx.symbolProvider.toSymbol(shape).name
            CborDeserializeUnionGenerator(ctx, name, members, writer).render()
        } else {
            CborDeserializeStructGenerator(ctx, members, writer).render()
        }
    }

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
            writer.openBlock("private fun #L(builder: #T.Builder, payload: ByteArray) {", fnName, symbol)
                .call {
                    writer.write("val deserializer = #T(payload)", RuntimeTypes.Serde.SerdeCbor.CborDeserializer)
                    renderDeserializerBody(ctx, errorShape, members, writer)
                }
                .closeBlock("}")
        }
    }
}

/**
 * An implementation of [DeserializeStructGenerator] which renders custom deserialization functions for CBOR types.
 */
private open class CborDeserializeStructGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    members: List<MemberShape>,
    writer: KotlinWriter,
) : DeserializeStructGenerator(ctx, members, writer, TimestampFormatTrait.Format.EPOCH_SECONDS) {

    override fun renderShapeDeserializer(memberShape: MemberShape) {
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        val descriptorName = memberShape.descriptorName()
        val deserialize = deserializeFnForShape(memberShape)
        writer.write("$descriptorName.index -> builder.$memberName = $deserialize")
    }

    /**
     * Return Kotlin function that deserializes a primitive value.
     * @param shape primitive [Shape] associated with value.
     */
    private fun deserializeFnForShape(shape: Shape): String {
        // target shape type to deserialize is either the shape itself or member.target
        val target = shape.targetOrSelf(ctx.model)

        return when {
            target.type == ShapeType.BOOLEAN -> "deserializeBoolean()"
            target.type == ShapeType.BYTE -> "deserializeByte()"
            target.type == ShapeType.SHORT -> "deserializeShort()"
            target.type == ShapeType.INTEGER -> "deserializeInt()"
            target.type == ShapeType.LONG -> "deserializeLong()"
            target.type == ShapeType.FLOAT -> "deserializeFloat()"
            target.type == ShapeType.DOUBLE -> "deserializeDouble()"
            target.type == ShapeType.BIG_INTEGER -> "deserializeBigInteger()"
            target.type == ShapeType.BIG_DECIMAL -> "deserializeBigDecimal()"
            target.type == ShapeType.DOCUMENT -> "deserializeDocument()"

            target.type == ShapeType.BLOB -> "deserializeBlob()" // note: custom function only present in CborDeserializer
            target.type == ShapeType.TIMESTAMP -> "deserializeTimestamp()" // note: custom function only present in CborDeserializer

            target.isStringEnumShape -> {
                val enumSymbol = ctx.symbolProvider.toSymbol(target)
                writer.addImport(enumSymbol)
                "deserializeString().let { ${enumSymbol.name}.fromValue(it) }"
            }

            target.isIntEnumShape -> {
                val enumSymbol = ctx.symbolProvider.toSymbol(target)
                writer.addImport(enumSymbol)
                "deserializeInt().let { ${enumSymbol.name}.fromValue(it) }"
            }

            target.type == ShapeType.STRING -> "deserializeString()"

            target.type == ShapeType.STRUCTURE || target.type == ShapeType.UNION -> {
                val symbol = ctx.symbolProvider.toSymbol(target)
                val deserializerName = symbol.documentDeserializerName()
                "$deserializerName(deserializer)"
            }

            else -> throw CodegenException("unknown deserializer for member: $shape; target: $target")
        }
    }
}

/**
 * Copy of [DeserializeUnionGenerator] which delegates to [CborDeserializeStructGenerator] instead of [DeserializeStructGenerator].
 */
private class CborDeserializeUnionGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    private val unionName: String,
    members: List<MemberShape>,
    writer: KotlinWriter,
) : CborDeserializeStructGenerator(ctx, members, writer) {
    /**
     * Iterate over all supplied [MemberShape]s to generate serializers.
     */
    override fun render() {
        // inline an empty object descriptor when the struct has no members
        // otherwise use the one generated as part of the companion object
        val objDescriptor = if (members.isNotEmpty()) "OBJ_DESCRIPTOR" else "SdkObjectDescriptor.build {}"
        writer.withBlock("deserializer.deserializeStruct($objDescriptor) {", "}") {
            // field iterators MUST be driven to completion so that underlying tokens are consumed
            // and the deserializer state is maintained
            withBlock("loop@while(true) {", "}") {
                withBlock("when(findNextFieldIndex()) {", "}") {
                    members
                        .sortedBy { it.memberName }
                        .forEach { memberShape -> renderMemberShape(memberShape) }
                    write("null -> break@loop")
                    write("else -> value = $unionName.SdkUnknown.also { skipValue() }")
                }
            }
        }
    }

    /**
     * Deserialize top-level members.
     */
    override fun renderMemberShape(memberShape: MemberShape) {
        when (val targetShape = ctx.model.expectShape(memberShape.target)) {
            is ListShape -> renderListMemberDeserializer(memberShape, targetShape as CollectionShape)
            is MapShape -> renderMapMemberDeserializer(memberShape, targetShape)
            is StructureShape,
            is UnionShape,
            -> renderShapeDeserializer(memberShape)
            is BlobShape,
            is BooleanShape,
            is StringShape,
            is TimestampShape,
            is ByteShape,
            is ShortShape,
            is IntegerShape,
            is LongShape,
            is FloatShape,
            is DoubleShape,
            is BigDecimalShape,
            is DocumentShape,
            is BigIntegerShape,
            -> renderShapeDeserializer(memberShape)
            else -> error("Unexpected shape type: ${targetShape.type}")
        }
    }

    /**
     * Generate the union deserializer for a primitive member. Example:
     * ```
     * I32_DESCRIPTOR.index -> value = deserializeInt().let { PrimitiveUnion.I32(it) }
     * ```
     */
    override fun renderShapeDeserializer(memberShape: MemberShape) {
        val unionTypeName = memberShape.unionTypeName(ctx)
        val descriptorName = memberShape.descriptorName()
        val deserialize = deserializerForShape(memberShape)

        writer.write("$descriptorName.index -> value = $unionTypeName($deserialize)")
    }

    // Union response types hold a single value for any variant
    override fun deserializationResultName(defaultName: String): String = "value"

    // Return the type that deserializes the incoming value.  Example: `MyAggregateUnion.IntList`
    override fun collectionReturnExpression(memberShape: MemberShape, defaultCollectionName: String): String {
        val unionTypeName = memberShape.unionTypeName(ctx)
        return "$unionTypeName($defaultCollectionName)"
    }
}
