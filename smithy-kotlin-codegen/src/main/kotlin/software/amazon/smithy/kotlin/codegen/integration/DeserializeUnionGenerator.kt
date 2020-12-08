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
import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.SymbolVisitor
import software.amazon.smithy.kotlin.codegen.withBlock
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.utils.StringUtils

/**
 * Generate deserialization for unions.
 *
 * e.g.
 * ```
 * deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
 *      when(findNextFieldIndex()) {
 *          I32_DESCRIPTOR.index -> value = MyUnion.I32(deserializeInt()!!)
 *          STRINGA_DESCRIPTOR.index -> value = MyUnion.StringA(deserializeString()!!)
 *          else -> skipValue()
 *      }
 * }
 * ```
 */
class DeserializeUnionGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) {

    fun render() {
        writer.withBlock("deserializer.deserializeStruct(OBJ_DESCRIPTOR) {", "}") {
            withBlock("when(findNextFieldIndex()) {", "}") {
                members.forEach { member ->
                    val target = ctx.model.expectShape(member.target)
                    when (target.type) {
                        ShapeType.LIST, ShapeType.SET -> deserializeListMember(member)
                        ShapeType.MAP -> deserializeMapMember(member)
                        // TODO - implement document type support
                        ShapeType.DOCUMENT -> writer.write("\$L.index -> skipValue()", member.descriptorName())
                        ShapeType.UNION -> {
                            val targetShape = ctx.model.expectShape(member.target)
                            for (targetMember in targetShape.members()) {
                                val nestedMember = ctx.model.expectShape(targetMember.target.toShapeId())
                                when (nestedMember) {
                                    is MapShape -> deserializeMapMember(targetMember)
                                    is CollectionShape -> deserializeListMember(targetMember)
                                    else -> {
                                        val deserialize = deserializerForShape(targetMember)
                                        val targetType = target.unionTypeName(targetMember)
                                        writer.write("\$L.index -> value = $deserialize?.let { \$L(it) }", targetMember.descriptorName(), targetType)
                                    }
                                }
                            }
                        }
                        else -> {
                            val deserialize = deserializerForShape(member)
                            val targetType = member.unionTypeName(member)
                            writer.write("\$L.index -> value = $deserialize?.let { \$L(it) }", member.descriptorName(), targetType)
                        }
                    }
                }
                write("else -> skipValue()")
            }
        }
    }

    /**
     * get the deserializer name for the given [Shape], this only handles "primitive" types, collections
     * should be handled separately
     *
     * MyUnion.StringValue(deserializeString())
     * deserializeString()?.let { MyUnion.StringValue(it) }
     */
    private fun deserializerForShape(shape: Shape): String {
        // target shape type to deserialize is either the shape itself or member.target
        val target = when (shape) {
            is MemberShape -> ctx.model.expectShape(shape.target)
            else -> shape
        }

        return when (target.type) {
            ShapeType.BOOLEAN -> "deserializeBool()"
            ShapeType.BYTE -> "deserializeByte()"
            ShapeType.SHORT -> "deserializeShort()"
            ShapeType.INTEGER -> "deserializeInt()"
            ShapeType.LONG -> "deserializeLong()"
            ShapeType.FLOAT -> "deserializeFloat()"
            ShapeType.DOUBLE -> "deserializeDouble()"
            ShapeType.BLOB -> {
                importBase64Utils(writer)
                "deserializeString()?.decodeBase64Bytes()"
            }
            ShapeType.TIMESTAMP -> {
                importInstant(writer)
                val tsFormat = shape
                    .getTrait(TimestampFormatTrait::class.java)
                    .map { it.format }
                    .orElse(defaultTimestampFormat)

                when (tsFormat) {
                    TimestampFormatTrait.Format.EPOCH_SECONDS -> "deserializeString()?.let { Instant.fromEpochSeconds(it) }"
                    TimestampFormatTrait.Format.DATE_TIME -> "deserializeString()?.let { Instant.fromIso8601(it) }"
                    TimestampFormatTrait.Format.HTTP_DATE -> "deserializeString()?.let { Instant.fromRfc5322(it) }"
                    else -> throw CodegenException("unknown timestamp format: $tsFormat")
                }
            }
            ShapeType.STRING -> when {
                target.hasTrait(EnumTrait::class.java) -> {
                    val enumSymbol = ctx.symbolProvider.toSymbol(target)
                    writer.addImport(enumSymbol)
                    "deserializeString()?.let { ${enumSymbol.name}.fromValue(it) }"
                }
                else -> "deserializeString()"
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                val symbol = ctx.symbolProvider.toSymbol(target)
                writer.addImport(symbol)
                val deserializerName = "${symbol.name}Deserializer"
                "$deserializerName().deserialize(deserializer)"
            }
            else -> throw CodegenException("unknown deserializer for member: $shape; target: $target; type: ${target.type}")
        }
    }

    private fun deserializeListMember(member: MemberShape) {
        writer.write("\$L.index -> value = ", member.descriptorName())
            .indent()
            .call {
                val collectionShape = ctx.model.expectShape(member.target) as CollectionShape
                val collectionIsSet = collectionShape is SetShape
                val targetShape = ctx.model.expectShape(collectionShape.member.target)
                renderDeserializeList(member, targetShape, renderAsSet = collectionIsSet)
            }
            .dedent()
    }

    private fun renderDeserializeList(
        member: MemberShape,
        targetShape: Shape,
        level: Int = 0,
        renderAsSet: Boolean = false
    ) {
        val targetSymbol = ctx.symbolProvider.toSymbol(targetShape)
        val destList = "list$level"
        val elementName = "el$level"
        val conversion = if (renderAsSet) ".toSet()" else ""
        val targetType = member.unionTypeName(member)

        val descriptorName = if (level == 0) member.descriptorName() else member.descriptorName("_C${level - 1}")

        writer.openBlock("deserializer.deserializeList(\$L) {", descriptorName)
            .write("val $destList = mutableListOf<${targetSymbol.name}>()")
            .openBlock("while(hasNextElement()) {")
            .call {
                when (targetShape) {
                    is CollectionShape -> {
                        writer.write("val $elementName =")
                        val nestedTarget = ctx.model.expectShape(targetShape.member.target)
                        renderDeserializeList(member, nestedTarget, level + 1)
                    }
                    is MapShape -> {
                        writer.write("val $elementName =")
                        val nestedTarget = ctx.model.expectShape(targetShape.value.target)
                        renderDeserializeMap(member, targetShape, nestedTarget, 0)
                    }
                    else -> {
                        val deserializeForElement = deserializerForShape(targetShape)
                        writer.write("val $elementName = $deserializeForElement")
                    }
                }
                writer.write("if ($elementName != null) $destList.add($elementName)")
            }
            .closeBlock("}")
            // implicit return of `deserializeList` lambda is last expression
            .write("$targetType($destList$conversion)")
            .closeBlock("}")
    }

    private fun deserializeMapMember(member: MemberShape) {
        writer.write("\$L.index -> value = ", member.descriptorName())
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
        val sparseMap = ctx.model.expectShape(memberShape.target).hasTrait(SparseTrait::class.java)
        val mutableCollection = ctx.symbolProvider.toSymbol(collectionShape).expectProperty(SymbolVisitor.MUTABLE_COLLECTION_FUNCTION)
        val targetType = memberShape.unionTypeName(memberShape)

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
                        renderDeserializeList(memberShape, nestedTarget, level + 1)
                    }
                    is MapShape -> {
                        writer.write("val $elementName =")
                        val nestedTarget = ctx.model.expectShape(targetShape.value.target)
                        renderDeserializeMap(memberShape, targetShape, nestedTarget, level + 1)
                    }
                    else -> {
                        val deserializeForElement = deserializerForShape(targetShape)
                        writer.write("val $elementName = $deserializeForElement")
                    }
                }

                if (sparseMap) {
                    writer.write("$destMap[$keyName] = $elementName")
                } else {
                    writer.write("if ($elementName != null) $destMap[$keyName] = $elementName")
                }
            }
            .closeBlock("}")
            // implicit return of `deserializeMap` lambda is last expression
            .write("$targetType($destMap)")
            .closeBlock("}")
    }
}

/**
 * Generate the fully qualified type name of Union variant
 */
internal fun Shape.unionTypeName(unionVariant: MemberShape): String = "${this.id.name}.${StringUtils.capitalize(unionVariant.memberName)}"
