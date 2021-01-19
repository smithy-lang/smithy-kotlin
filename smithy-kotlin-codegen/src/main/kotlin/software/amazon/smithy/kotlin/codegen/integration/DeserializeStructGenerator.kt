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
class DeserializeStructGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) {

    fun render() {
        // inline an empty object descriptor when the struct has no members
        // otherwise use the one generated as part of the companion object
        val objDescriptor = if (members.isNotEmpty()) "OBJ_DESCRIPTOR" else "SdkObjectDescriptor.build{}"
        writer.withBlock("deserializer.deserializeStruct($objDescriptor) {", "}") {
            withBlock("loop@while(true) {", "}") {
                withBlock("when(findNextFieldIndex()) {", "}") {
                    members.forEach { member ->
                        val target = ctx.model.expectShape(member.target)
                        when (target.type) {
                            ShapeType.LIST, ShapeType.SET -> deserializeListMember(member)
                            ShapeType.MAP -> deserializeMapMember(member)
                            // TODO - implement document type support
                            ShapeType.DOCUMENT -> writer.write("\$L.index -> skipValue()", member.descriptorName())
                            else -> {
                                val deserialize = deserializerForShape(member)
                                writer.write("\$L.index -> builder.\$L = $deserialize", member.descriptorName(), member.defaultName())
                            }
                        }
                    }
                    write("null -> break@loop")
                    write("else -> skipValue()")
                }
            }
        }
    }

    /**
     * get the deserializer name for the given [Shape], this only handles "primitive" types, collections
     * should be handled separately
     */
    private fun deserializerForShape(shape: Shape): String {
        // target shape type to deserialize is either the shape itself or member.target
        val target = when (shape) {
            is MemberShape -> ctx.model.expectShape(shape.target)
            else -> shape
        }

        return when (target.type) {
            ShapeType.BOOLEAN -> "deserializeBoolean()"
            ShapeType.BYTE -> "deserializeByte()"
            ShapeType.SHORT -> "deserializeShort()"
            ShapeType.INTEGER -> "deserializeInt()"
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

    private fun deserializeListMember(member: MemberShape) {
        writer.write("\$L.index -> builder.\$L = ", member.descriptorName(), member.defaultName())
            .indent()
            .call {
                val collectionShape = ctx.model.expectShape(member.target) as CollectionShape
                val collectionIsSet = collectionShape is SetShape
                val targetShape = ctx.model.expectShape(collectionShape.member.target)
                renderDeserializeList(member, member, targetShape, renderAsSet = collectionIsSet)
            }
            .dedent()
    }

    // FIXME - we should not have to pass through "member" through all nested levels, it should ideally only be required
    // in `deserializeListMember` or `deserializeMapMember`
    private fun renderDeserializeList(
        memberShape: MemberShape,
        collectionShape: Shape,
        targetShape: Shape,
        level: Int = 0,
        renderAsSet: Boolean = false
    ) {
        val mutableCollectionFunction = ctx.symbolProvider.toSymbol(collectionShape).expectProperty(SymbolVisitor.MUTABLE_COLLECTION_FUNCTION)

        val isSparse = ctx.model.expectShape(memberShape.target).hasTrait(SparseTrait::class.java)
        val destList = "list$level"
        val elementName = "el$level"
        val conversion = if (renderAsSet) ".toSet()" else ""

        val listDescriptorName = if (level == 0) memberShape.descriptorName() else memberShape.descriptorName("_C${level - 1}")
        writer.openBlock("deserializer.deserializeList(\$L) {", listDescriptorName)
            .write("val $destList = $mutableCollectionFunction()")
            .openBlock("while(hasNextElement()) {")
            .call {
                when (targetShape) {
                    is CollectionShape -> {
                        writer.write("val $elementName =")
                        val nestedTarget = ctx.model.expectShape(targetShape.member.target)
                        renderDeserializeList(memberShape, targetShape, nestedTarget, level + 1)
                    }
                    is MapShape -> {
                        writer.write("val $elementName =")
                        val nestedTarget = ctx.model.expectShape(targetShape.value.target)
                        renderDeserializeMap(memberShape, targetShape, nestedTarget, 0)
                    }
                    else -> {
                        val deserializeForElement = deserializerForShape(targetShape)
                        when (isSparse) {
                            true -> writer.write("val $elementName = if (nextHasValue()) $deserializeForElement else deserializeNull()")
                            false -> writer.write("val $elementName = if (nextHasValue()) { $deserializeForElement } else { deserializeNull(); continue }")
                        }
                    }
                }
                writer.write("$destList.add($elementName)")
            }
            .closeBlock("}")
            // implicit return of `deserializeList` lambda is last expression
            .write("$destList$conversion")
            .closeBlock("}")
    }

    private fun deserializeMapMember(member: MemberShape) {
        writer.write("\$L.index -> builder.\$L = ", member.descriptorName(), member.defaultName())
            .indent()
            .call {
                val mapShape = ctx.model.expectShape(member.target) as MapShape
                val targetShape = ctx.model.expectShape(mapShape.value.target)
                renderDeserializeMap(member, member, targetShape)
            }
            .dedent()
    }

    private fun renderDeserializeMap(
        memberShape: MemberShape,
        collectionShape: Shape,
        targetShape: Shape,
        level: Int = 0
    ) {
        val destMap = "map$level"
        val elementName = "el$level"
        val isSparse = ctx.model.expectShape(memberShape.target).hasTrait(SparseTrait::class.java)
        val mutableCollection = ctx.symbolProvider.toSymbol(collectionShape).expectProperty(SymbolVisitor.MUTABLE_COLLECTION_FUNCTION)

        writer.openBlock("deserializer.deserializeMap(\$L) {", memberShape.descriptorName())
            .write("val $destMap = $mutableCollection()")
            .openBlock("while(hasNextEntry()) {")
            .call {
                val keyName = "k$level"
                // key() needs to be called first to keep deserialization state in correct order
                writer.write("val $keyName = key()")
                when (targetShape) {
                    is CollectionShape -> {
                        writer.write("val $elementName =")
                        val nestedTarget = ctx.model.expectShape(targetShape.member.target)
                        // FIXME - what would we pass here. The descriptor describes the map not a list
                        renderDeserializeList(memberShape, targetShape, nestedTarget, level + 1)
                    }
                    is MapShape -> {
                        writer.write("val $elementName =")
                        val nestedTarget = ctx.model.expectShape(targetShape.value.target)
                        renderDeserializeMap(memberShape, targetShape, nestedTarget, level + 1)
                    }
                    is StructureShape, is SimpleShape -> {
                        val deserializeForElement = deserializerForShape(targetShape)
                        when (isSparse) {
                            true ->
                                writer.write("val $elementName = if (nextHasValue()) $deserializeForElement else deserializeNull()")
                            false ->
                                writer.write("val $elementName = if (nextHasValue()) { $deserializeForElement } else { deserializeNull(); continue }")
                        }
                    }
                    else -> TODO("Unhandled codegen path for $targetShape")
                }
                writer.write("$destMap[$keyName] = $elementName")
            }
            .closeBlock("}")
            // implicit return of `deserializeMap` lambda is last expression
            .write(destMap)
            .closeBlock("}")
    }
}
