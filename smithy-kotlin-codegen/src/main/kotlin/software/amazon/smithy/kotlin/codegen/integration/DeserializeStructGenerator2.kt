/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Generate deserialization for members bound to the payload.
 *
 * e.g.
 * ```
 * deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
 *    loop@while(true) {
 *        when(findNextFieldIndex()) {
 *             FIELD1_DESCRIPTOR.index -> builder.field1 = deserializeString()
 *             FIELD2_DESCRIPTOR.index -> builder.field2 = deserializeInt()
 *             null -> break@loop
 *             else -> skipValue()
 *         }
 *     }
 * }
 * ```
 */
class DeserializeStructGenerator2(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) {

    /**
     * Iterate over all supplied [MemberShape]s to generate serializers.
     */
    open fun render() {
        // inline an empty object descriptor when the struct has no members
        // otherwise use the one generated as part of the companion object
        val objDescriptor = if (members.isNotEmpty()) "OBJ_DESCRIPTOR" else "SdkObjectDescriptor.build {}"
        writer.withBlock("deserializer.deserializeStruct($objDescriptor) {", "}") {
            withBlock("loop@while(true) {", "}") {
                withBlock("when(findNextFieldIndex()) {", "}") {
                    members.sortedBy { it.memberName }.forEach { memberShape ->
                        renderMemberShape(memberShape)
                        write("null -> break@loop")
                        write("else -> skipValue()")
                    }
                }
            }
        }
    }

    private fun renderMemberShape(memberShape: MemberShape) {
        val targetShape = ctx.model.expectShape(memberShape.target)

        when (targetShape.type) {
            ShapeType.LIST,
            ShapeType.SET -> renderListMemberDeserializer(memberShape, targetShape as CollectionShape)
            ShapeType.MAP -> renderMapMemberDeserializer(memberShape, targetShape as MapShape)
            ShapeType.STRUCTURE,
            ShapeType.UNION -> renderPrimitiveShapeDeserializer(memberShape, ::deserializerForStructureShape)
            ShapeType.DOCUMENT -> renderDocumentShapeDeserializer(memberShape)
            ShapeType.BLOB,
            ShapeType.BOOLEAN,
            ShapeType.STRING,
            ShapeType.TIMESTAMP,
            ShapeType.BYTE,
            ShapeType.SHORT,
            ShapeType.INTEGER,
            ShapeType.LONG,
            ShapeType.FLOAT,
            ShapeType.DOUBLE,
            ShapeType.BIG_DECIMAL,
            ShapeType.BIG_INTEGER -> renderPrimitiveShapeDeserializer(memberShape, ::deserializerForPrimitiveShape)
            else -> error("Unexpected shape type: ${targetShape.type}")
        }
    }

    private fun renderDocumentShapeDeserializer(memberShape: MemberShape) {
        TODO("Not yet implemented")
    }

    private fun renderPrimitiveShapeDeserializer(memberShape: MemberShape, any: Any) {
        val memberName = memberShape.defaultName()
        val descriptorName = memberShape.descriptorName()
        val deserialize = deserializerForPrimitiveShape(memberShape)

        writer.write("$descriptorName.index -> builder.$memberName = $deserialize")
    }

    /**
     * Example:
     * ```
     * PAYLOAD_DESCRIPTOR.index -> builder.payload =
     * deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
     *      ...
     * }
     */
    private fun renderMapMemberDeserializer(memberShape: MemberShape, targetShape: MapShape) {
        val nestingLevel = 0
        val memberName = memberShape.defaultName()
        val descriptorName = memberShape.descriptorName()
        val collectionType = targetShape.collectionElementType(ctx).name

        writer.write("$descriptorName.index -> builder.$memberName = ")
            .indent()
            .withBlock("deserializer.deserializeMap($descriptorName) {", "}") {
                write("val map0 = mutableMapOf<String, $collectionType>()")
                withBlock("while(hasNextEntry()) {", "}") {
                    delegateMapDeserialization(memberShape, targetShape, nestingLevel, memberName)
                }
                write("map0")
            }
            .dedent()
    }

    private fun delegateMapDeserialization(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        val elementShape = ctx.model.expectShape(mapShape.value.target)
        val isSparse = mapShape.hasTrait(SparseTrait::class.java)

        when (elementShape.type) {
            ShapeType.BOOLEAN,
            ShapeType.STRING,
            ShapeType.BYTE,
            ShapeType.SHORT,
            ShapeType.INTEGER,
            ShapeType.LONG,
            ShapeType.FLOAT,
            ShapeType.DOUBLE,
            ShapeType.BIG_DECIMAL,
            ShapeType.BIG_INTEGER -> renderPrimitiveEntry(elementShape, nestingLevel, parentMemberName)
            ShapeType.BLOB -> renderBlobEntry(nestingLevel, parentMemberName)
            ShapeType.TIMESTAMP -> renderTimestampEntry(elementShape, nestingLevel, parentMemberName)
            ShapeType.SET,
            ShapeType.LIST -> renderListEntry(rootMemberShape, elementShape as CollectionShape, nestingLevel, parentMemberName)
            ShapeType.MAP -> renderMapEntry(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE -> renderNestedStructureEntry(elementShape, nestingLevel, parentMemberName, isSparse)
            else -> error("Unhandled type ${elementShape.type}")
        }
    }

    private fun renderNestedStructureEntry(
        elementShape: Shape,
        nestingLevel: Int,
        parentMemberName: String,
        sparse: Boolean
    ) {
        TODO("Not yet implemented")
    }

    private fun renderMapEntry(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        TODO("Not yet implemented")
    }

    private fun renderListEntry(
        rootMemberShape: MemberShape,
        collectionShape: CollectionShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        TODO("Not yet implemented")
    }

    private fun renderTimestampEntry(elementShape: Shape, nestingLevel: Int, parentMemberName: String) {
        TODO("Not yet implemented")
    }

    private fun renderBlobEntry(nestingLevel: Int, parentMemberName: String) {
        TODO("Not yet implemented")
    }

    /**
     * Example:
     * ```
     * val k0 = key()
     * val el0 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
     * map0[k0] = el0
     * ```
     */
    private fun renderPrimitiveEntry(elementShape: Shape, nestingLevel: Int, parentMemberName: String) {
        val deserializerFn = deserializerForPrimitiveShape(elementShape)

        writer.write("val k0 = key()")
        writer.write("val el0 = if (nextHasValue()) { $deserializerFn } else { deserializeNull(); continue }")
        writer.write("map0[k0] = el0")
    }

    /**
     * Example:
     * ```
     * PAYLOAD_DESCRIPTOR.index -> builder.payload =
     *  deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
     *      val list0 = mutableListOf<Instant>()
     *      while(hasNextElement()) {
     *          val el0 = if (nextHasValue()) { deserializeString().let { Instant.fromEpochSeconds(it) } } else { deserializeNull(); continue }
     *          list0.add(el0)
     *      }
     *      list0
     *  }
     */
    private fun renderListMemberDeserializer(memberShape: MemberShape, targetShape: CollectionShape) {
        val nestingLevel = 0
        val memberName = memberShape.defaultName()
        val descriptorName = memberShape.descriptorName()
        val collectionType = targetShape.collectionElementType(ctx).name

        writer.write("$descriptorName.index -> builder.$memberName = ")
            .indent()
            .withBlock("deserializer.deserializeList($descriptorName) {", "}") {
                write("val list0 = mutableListOf<$collectionType>()")
                withBlock("while(hasNextElement()) {", "}") {
                    delegateListDeserialization(memberShape, targetShape, nestingLevel, memberName)
                }
                write("list0")
            }
            .dedent()
    }

    private fun CollectionShape.collectionElementType(context: ProtocolGenerator.GenerationContext): Symbol =
        context.symbolProvider.toSymbol(context.model.expectShape(member.target))

    private fun MapShape.collectionElementType(context: ProtocolGenerator.GenerationContext): Symbol =
        context.symbolProvider.toSymbol(context.model.expectShape(value.target))


    private fun delegateListDeserialization(rootMemberShape: MemberShape, listShape: CollectionShape, nestingLevel: Int, parentMemberName: String) {
        val elementShape = ctx.model.expectShape(listShape.member.target)
        val isSparse = listShape.hasTrait(SparseTrait::class.java)

        when (elementShape.type) {
            ShapeType.BOOLEAN,
            ShapeType.STRING,
            ShapeType.BYTE,
            ShapeType.SHORT,
            ShapeType.INTEGER,
            ShapeType.LONG,
            ShapeType.FLOAT,
            ShapeType.DOUBLE,
            ShapeType.BIG_DECIMAL,
            ShapeType.BIG_INTEGER -> renderPrimitiveElement(elementShape, nestingLevel, parentMemberName, isSparse)
            ShapeType.BLOB -> renderBlobElement(nestingLevel, parentMemberName)
            ShapeType.TIMESTAMP -> renderTimestampElement(elementShape, nestingLevel, parentMemberName)
            ShapeType.LIST,
            ShapeType.SET -> renderListElement(rootMemberShape, elementShape as CollectionShape, nestingLevel, parentMemberName)
            ShapeType.MAP -> renderMapElement(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE -> renderNestedStructureElement(elementShape, nestingLevel, parentMemberName)
            else -> error("Unhandled type ${elementShape.type}")
        }
    }

    private fun renderNestedStructureElement(elementShape: Shape, nestingLevel: Int, parentMemberName: String) {
        //TODO("Not yet implemented")
    }

    private fun renderMapElement(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        //TODO("Not yet implemented")
    }

    private fun renderListElement(rootMemberShape: MemberShape, elementShape: CollectionShape, nestingLevel: Int, parentListMemberName: String) {
        //TODO("Not yet implemented")
    }

    /**
     * Example:
     * ```
     * val el0 = if (nextHasValue()) { deserializeString().let { Instant.fromEpochSeconds(it) } } else { deserializeNull(); continue }
     * list0.add(el0)
     * ```
     */
    private fun renderTimestampElement(elementShape: Shape, nestingLevel: Int, listMemberName: String) {
        val deserializer = deserializerForPrimitiveShape(elementShape)
        writer.write("val el0 = if (nextHasValue()) { $deserializer } else { deserializeNull(); continue }")
        writer.write("list0.add(el0)")
    }

    private fun renderBlobElement(nestingLevel: Int, listMemberName: String) {
        //TODO("Not yet implemented")
    }

    /**
     * Example:
     * ```
     * val el0 = if (nextHasValue()) { deserializeInteger() } else { deserializeNull(); continue }
     * list0.add(el0)
     * ```
     */
    private fun renderPrimitiveElement(elementShape: Shape, nestingLevel: Int, listMemberName: String, isSparse: Boolean) {
        val deserializerFn = deserializerForPrimitiveShape(elementShape)

        writer.write("val el0 = if (nextHasValue()) { $deserializerFn } else { deserializeNull(); continue }")
        writer.write("list0.add(el0)")
    }

    private fun deserializerForStructureShape(shape: Shape): SerializeStructGenerator.SerializeInfo {
        TODO("nOT")
    }

    private fun deserializerForPrimitiveShape(shape: Shape): String {
        // target shape type to deserialize is either the shape itself or member.target
        val target = shape.targetOrSelf(ctx.model)

        return when (target.type) {
            ShapeType.BOOLEAN -> "deserializeBoolean()"
            ShapeType.BYTE -> "deserializeByte()"
            ShapeType.SHORT -> "deserializeShort()"
            ShapeType.INTEGER -> "deserializeInteger()"
            ShapeType.LONG -> "deserializeLong()"
            ShapeType.FLOAT -> "deserializeFloat()"
            ShapeType.DOUBLE -> "deserializeDouble()"
            ShapeType.BLOB -> {
                importBase64Utils(writer)
                "deserializeString().decodeBase64Bytes()"
            }
            ShapeType.TIMESTAMP -> {
                importInstant(writer)
                val tsFormat = shape
                    .getTrait(TimestampFormatTrait::class.java)
                    .map { it.format }
                    .orElse(defaultTimestampFormat)

                when (tsFormat) {
                    TimestampFormatTrait.Format.EPOCH_SECONDS -> "deserializeString().let { Instant.fromEpochSeconds(it) }"
                    TimestampFormatTrait.Format.DATE_TIME -> "deserializeString().let { Instant.fromIso8601(it) }"
                    TimestampFormatTrait.Format.HTTP_DATE -> "deserializeString().let { Instant.fromRfc5322(it) }"
                    else -> throw CodegenException("unknown timestamp format: $tsFormat")
                }
            }
            ShapeType.STRING -> when {
                target.hasTrait(EnumTrait::class.java) -> {
                    val enumSymbol = ctx.symbolProvider.toSymbol(target)
                    writer.addImport(enumSymbol)
                    "deserializeString().let { ${enumSymbol.name}.fromValue(it) }"
                }
                else -> "deserializeString()"
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                val symbol = ctx.symbolProvider.toSymbol(target)
                writer.addImport(symbol)
                val deserializerName = "${symbol.name}Deserializer"
                "$deserializerName().deserialize(deserializer)"
            }
            else -> throw CodegenException("unknown deserializer for member: $shape; target: $target")
        }
    }
}
