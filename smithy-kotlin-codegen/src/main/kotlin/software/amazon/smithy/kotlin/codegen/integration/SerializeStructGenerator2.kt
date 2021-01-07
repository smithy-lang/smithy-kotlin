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
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.IdempotencyTokenTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Generate serialization for members bound to the payload.
 *
 * There are some proper names Smithy models use which can lead to confusion in codegen.
 * In this file, `member` refers to the lhs of a child of a structure (known as a member). It can
 * be thought of as the root node from which serialization of a field begins.
 *
 * `target` refers to the rhs of a child in a structure, which is a reference to another Smithy type.
 *
 * The element of a list (which is also known as a `member` in Smithy) is referred to as an element.
 *
 * Example output this class generates:
 * ```
 * serializer.serializeStruct(OBJ_DESCRIPTOR) {
 *     input.field1?.let { field(FIELD1_DESCRIPTOR, it) }
 *     input.field2?.let { field(FIELD2_DESCRIPTOR, it) }
 * }
 * ```
 */
class SerializeStructGenerator2(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) {

    /**
     * Iterate over all supplied [MemberShape]s to generate serializers.
     */
    fun render() {
        writer.withBlock("serializer.serializeStruct(OBJ_DESCRIPTOR) {", "}") {
            members.sortedBy { it.memberName }.forEach { memberShape ->
                renderMemberShape(memberShape)
            }
        }
    }

    /**
     * Call appropriate serialization function based on target type of member shape.
     */
    private fun renderMemberShape(memberShape: MemberShape) {
        val targetShape = ctx.model.expectShape(memberShape.target)

        when (targetShape.type) {
            ShapeType.LIST,
            ShapeType.SET -> renderListMemberSerializer(memberShape, targetShape as CollectionShape)
            ShapeType.MAP -> renderMapMemberSerializer(memberShape, targetShape as MapShape)
            ShapeType.STRUCTURE,
            ShapeType.UNION -> renderStructureShapeSerializer(memberShape)
            ShapeType.DOCUMENT -> renderDocumentShapeSerializer(memberShape)
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
            ShapeType.BIG_INTEGER -> renderPrimitiveShapeSerializer(memberShape)
            else -> error("Unexpected shape type: ${targetShape.type}")
        }
    }

    /**
     * Produces serialization for a map member.  Example:
     * ```
     * if (input.payload != null) {
     *     mapField(PAYLOAD_DESCRIPTOR) {
     *         ...
     *     }
     * }
     * ```
     */
    private fun renderMapMemberSerializer(memberShape: MemberShape, targetShape: MapShape) {
        val memberName = memberShape.defaultName()
        val descriptorName = memberShape.descriptorName()
        val nestingLevel = 0

        writer.withBlock("if (input.$memberName != null) {", "}") {
            writer.withBlock("mapField($descriptorName) {", "}") {
                delegateMapSerialization(memberShape, targetShape, nestingLevel, memberName)
            }
        }
    }

    /**
     * Produces serialization for a list member.  Example:
     * ```
     * if (input.intList != null) {
     *     listField(INTLIST_DESCRIPTOR) {
     *         ...
     *     }
     * }
     * ```
     */
    private fun renderListMemberSerializer(memberShape: MemberShape, targetShape: CollectionShape) {
        val memberName = memberShape.defaultName()
        val descriptorName = memberShape.descriptorName()
        val nestingLevel = 0

        writer.withBlock("if (input.$memberName != null) {", "}") {
            writer.withBlock("listField($descriptorName) {", "}") {
                delegateListSerialization(memberShape, targetShape, nestingLevel, memberName)
            }
        }
    }

    /**
     * Delegates to other functions based on the type of value target of map.
     */
    private fun delegateMapSerialization(rootMemberShape: MemberShape, mapShape: MapShape, nestingLevel: Int, parentMemberName: String) {
        val elementShape = ctx.model.expectShape(mapShape.value.target)

        when (elementShape.type) {
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
            ShapeType.BIG_INTEGER -> renderPrimitiveEntry(elementShape, nestingLevel, parentMemberName)
            ShapeType.LIST -> renderListEntry(rootMemberShape, mapShape, elementShape as ListShape, nestingLevel, parentMemberName)
            ShapeType.MAP -> renderMapEntry(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE -> renderNestedStructureEntry(elementShape as StructureShape, nestingLevel, parentMemberName)
            else -> error("Unhandled type ${elementShape.type}")
        }
    }

    /**
     * Delegates to other functions based on the type of element.
     */
    private fun delegateListSerialization(rootMemberShape: MemberShape, listShape: CollectionShape, nestingLevel: Int, parentMemberName: String) {
        val elementShape = ctx.model.expectShape(listShape.member.target)

        when (elementShape.type) {
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
            ShapeType.BIG_INTEGER -> renderPrimitiveElement(elementShape, nestingLevel, parentMemberName)
            ShapeType.LIST -> renderListElement(rootMemberShape, elementShape as ListShape, nestingLevel, parentMemberName)
            ShapeType.MAP -> renderMapElement(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE -> renderNestedStructureElement(elementShape as StructureShape, nestingLevel, parentMemberName)
            else -> error("Unhandled type ${elementShape.type}")
        }
    }

    /**
     * Renders the serialization of a nested Structure.  Example:
     *
     * ```
     * for(m0 in input.structList) {
     *     serializeSdkSerializable(NestedSerializer(m0))
     * }
     * ```
     */
    private fun renderNestedStructureElement(structureShape: StructureShape, nestingLevel: Int, parentMemberName: String) {
        val serializerFnName = structureShape.type.primitiveSerializerFunctionName()
        val serializerTypeName = "${structureShape.defaultName()}Serializer"
        val elementName = nestingLevel.nestedIdentifier()
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$parentMemberName) {", "}") {
            writer.write("$serializerFnName($serializerTypeName($elementName))")
        }
    }

    private fun renderNestedStructureEntry(structureShape: StructureShape, nestingLevel: Int, parentMemberName: String) {
        val serializerTypeName = "${structureShape.defaultName()}Serializer"
        val keyName = if (nestingLevel == 0) "key" else "key$nestingLevel"
        val valueName = if (nestingLevel == 0) "value" else "value$nestingLevel"
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.write("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> entry($keyName, $serializerTypeName($valueName)) }")
    }


    private fun renderMapElement(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.nestedIdentifier()
        val containerName = if (nestingLevel == 0) "input." else ""


        writer.withBlock("for ($elementName in $containerName$parentMemberName) {", "}") {
            writer.withBlock("serializer.serializeMap($descriptorName) {", "}") {
                delegateMapSerialization(rootMemberShape, mapShape, nestingLevel + 1, elementName)
            }
        }
    }

    /**
     * Render the serialization of a map entry.  Example:
     * ```
     * input.payload.forEach { (key, value) -> mapEntry(key, PAYLOAD_M0_DESCRIPTOR) {
     *     if (key != null) {
     *         mapField(SOME_DESCRIPTOR) {
     *             ...
     *         }
     *     }
     * }
     * ```
     */
    private fun renderMapEntry(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val containerName = if (nestingLevel == 0) "input." else ""
        val keyName = if (nestingLevel == 0) "key" else "key$nestingLevel"
        val valueName = if (nestingLevel == 0) "value" else "value$nestingLevel"

        writer.withBlock("$containerName${parentMemberName}.forEach { ($keyName, $valueName) -> mapEntry($keyName, $descriptorName) {", "}}") {
            delegateMapSerialization(rootMemberShape, mapShape, nestingLevel + 1, valueName)
        }

        /*
        writer.withBlock("$containerName${parentMemberName}.forEach { ($keyName, $valueName) -> mapEntry($keyName, $descriptorName) {", "}}") {
            writer.withBlock("if ($keyName != null) {", "}") {
                writer.withBlock("mapField(SOME_DESCRIPTOR) {", "}") {
                    delegateMapSerialization(rootMemberShape, mapShape, nestingLevel + 1, valueName)
                }
            }
        }
         */
    }

    private fun renderListEntry(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        elementShape: ListShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.nestedIdentifier()
        val containerName = if (nestingLevel == 0) "input." else ""
        val keyName = if (nestingLevel == 0) "key" else "key$nestingLevel"
        val valueName = if (nestingLevel == 0) "value" else "value$nestingLevel"

        writer.withBlock("$containerName${parentMemberName}.forEach { ($keyName, $valueName) -> listEntry($keyName, $descriptorName) {", "}}") {
            delegateListSerialization(rootMemberShape, elementShape, nestingLevel + 1, valueName)
        }
    }

    /**
     * Render a List element of type List
     *
     * Example:
     *
     * ```
     * for (m0 in input.payload) {
     *   serializer.serializeList(PAYLOAD_M0_DESCRIPTOR) {
     *      ...
     *   }
     * }
     */
    private fun renderListElement(rootMemberShape: MemberShape, elementShape: ListShape, nestingLevel: Int, parentListMemberName: String) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.nestedIdentifier()
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$parentListMemberName) {", "}") {
            writer.withBlock("serializer.serializeList($descriptorName) {", "}") {
                delegateListSerialization(rootMemberShape, elementShape, nestingLevel + 1, nestingLevel.nestedIdentifier())
            }
        }
    }

    private fun renderPrimitiveEntry(elementShape: Shape, nestingLevel: Int, listMemberName: String) {
        val serializerFnName = elementShape.type.primitiveSerializerFunctionName()
        val elementName = nestingLevel.nestedIdentifier()
        val containerName = if (nestingLevel == 0) "input." else ""
        val keyName = if (nestingLevel == 0) "key" else "key$nestingLevel"
        val valueName = if (nestingLevel == 0) "value" else "value$nestingLevel"

        writer.write("$containerName${listMemberName}.forEach { ($keyName, $valueName) -> entry($keyName, $valueName) }")
    }

    /**
     * Render a List element of a primitive type
     *
     * Example:
     * ```
     * for (m0 in input.payload) {
     *  serializeInt(m0)
     * }
     * ```
     */
    private fun renderPrimitiveElement(elementShape: Shape, nestingLevel: Int, listMemberName: String) {
        val serializerFnName = elementShape.type.primitiveSerializerFunctionName()
        val elementName = nestingLevel.nestedIdentifier()
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$listMemberName) {", "}") {
            writer.write("$serializerFnName($elementName)")
        }
    }

    private fun renderDocumentShapeSerializer(memberShape: MemberShape) {
        TODO("Not yet implemented")
    }

    private fun renderStructureShapeSerializer(memberShape: MemberShape) {
        val (serializeFn, encoded) = serializerForStructureShape(memberShape, "it", SerializeLocation.Field)
        val postfix = idempotencyTokenPostfix(memberShape)
        writer.write("input.\$L?.let { $serializeFn(\$L, $encoded) }$postfix", memberShape.defaultName(), memberShape.descriptorName())
    }

    private fun renderPrimitiveShapeSerializer(memberShape: MemberShape) {
        val (serializeFn, encoded) = serializerForPrimitiveShape(memberShape, "it", SerializeLocation.Field)
        // FIXME - this doesn't account for unboxed primitives
        val postfix = idempotencyTokenPostfix(memberShape)

        writer.write("input.\$L?.let { $serializeFn(\$L, $encoded) }$postfix", memberShape.defaultName(), memberShape.descriptorName())
    }

    private fun idempotencyTokenPostfix(memberShape: MemberShape): String =
        if (memberShape.hasTrait(IdempotencyTokenTrait::class.java)) {
            " ?: field(${memberShape.descriptorName()}, serializationContext.idempotencyTokenProvider.generateToken())"
        } else {
            ""
        }

    /**
     * Return the serializer function for a Structure or Union
     */
    private fun serializerForStructureShape(
        shape: Shape,
        identifier: String = "it",
        serializeLocation: SerializeLocation
    ): SerializeInfo {
        // target shape type to deserialize is either the shape itself or member.target
        val target = shape.targetOrSelf(ctx.model)
        // the Smithy type hierarchy is private such that tighter type handling at the function level isn't possible
        require(target.type == ShapeType.STRUCTURE || target.type == ShapeType.UNION) { "Unexpected serializer for member: $shape; target: $target"}

        val symbol = ctx.symbolProvider.toSymbol(target)
        val memberSerializerName = "${symbol.name}Serializer"

        // invoke the ctor of the serializer to delegate to and pass the value
        val encoded = when (serializeLocation) {
            SerializeLocation.Field -> "$memberSerializerName($identifier)"
            SerializeLocation.Map -> "if ($identifier != null) $memberSerializerName($identifier) else null"
        }

        return SerializeInfo(serializeLocation.serializerFn, encoded)
    }

    /**
     * get the serialization function and encoded value for the given [Shape], this only handles "primitive" types,
     * collections should be handled separately
     * @param shape The shape to be serialized
     * @param identifier The name of the identifier to be passed to the serialization function
     * @param serializeLocation The location being serialized to
     */
    private fun serializerForPrimitiveShape(
        shape: Shape,
        identifier: String = "it",
        serializeLocation: SerializeLocation
    ): SerializeInfo {
        // target shape type to deserialize is either the shape itself or member.target
        val target = shape.targetOrSelf(ctx.model)

        // our current generation of serializer interfaces unfortunately uses different names depending on whether
        // you are serializing struct fields, list elements, map entries, etc.
        var serializerFn = serializeLocation.serializerFn

        val encoded = when (target.type) {
            ShapeType.BOOLEAN,
            ShapeType.BYTE,
            ShapeType.SHORT,
            ShapeType.INTEGER,
            ShapeType.LONG,
            ShapeType.FLOAT,
            ShapeType.DOUBLE -> identifier
            ShapeType.BLOB -> {
                importBase64Utils(writer)
                "$identifier.encodeBase64String()"
            }
            ShapeType.TIMESTAMP -> {
                importTimestampFormat(writer)
                val tsFormat = shape
                    .getTrait(TimestampFormatTrait::class.java)
                    .map { it.format }
                    .orElse(defaultTimestampFormat)

                if (tsFormat == TimestampFormatTrait.Format.EPOCH_SECONDS) {
                    serializerFn = "raw${serializerFn.capitalize()}"
                }

                formatInstant(identifier, tsFormat, forceString = true)
            }
            ShapeType.STRING -> when {
                target.hasTrait(EnumTrait::class.java) -> "$identifier.value"
                else -> identifier
            }
            else -> throw CodegenException("unknown serializer for member: $shape; target: $target")
        }

        return SerializeInfo(serializerFn, encoded)
    }
}

private fun Shape.targetOrSelf(model: Model) = when (this) {
        is MemberShape -> model.expectShape(this.target)
        else -> this
    }
