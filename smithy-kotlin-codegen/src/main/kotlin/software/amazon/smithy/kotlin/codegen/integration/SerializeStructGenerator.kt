/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.IdempotencyTokenTrait
import software.amazon.smithy.model.traits.SparseTrait
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
 *
 * This class is open to extension for variations of member serialization; specifically Unions.
 */
open class SerializeStructGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) {

    /**
     * Container for serialization information for a particular shape being serialized to
     *
     * @property fn The name of the serialization function to use
     * @property encodedValue The value to pass to the serialization function
     */
    data class SerializeInfo(val fn: String, val encodedValue: String)

    /**
     * Returns the name to put in codegen to refer to the parent collection type.
     */
    open fun parentName(defaultName: String) = defaultName

    /**
     * Returns the name passed to the constructor of a nested serializer.
     */
    open fun valueToSerializeName(defaultName: String): String = defaultName

    /**
     * Iterate over all supplied [MemberShape]s to generate serializers.
     */
    open fun render() {
        // inline an empty object descriptor when the struct has no members
        // otherwise use the one generated as part of the companion object
        val objDescriptor = if (members.isNotEmpty()) "OBJ_DESCRIPTOR" else "SdkObjectDescriptor.build{}"
        writer.withBlock("serializer.serializeStruct($objDescriptor) {", "}") {
            members.sortedBy { it.memberName }.forEach { memberShape ->
                renderMemberShape(memberShape)
            }
        }
    }

    /**
     * Call appropriate serialization function based on target type of member shape.
     */
    protected fun renderMemberShape(memberShape: MemberShape) {
        val targetShape = ctx.model.expectShape(memberShape.target)

        when (targetShape.type) {
            ShapeType.LIST,
            ShapeType.SET -> renderListMemberSerializer(memberShape, targetShape as CollectionShape)
            ShapeType.MAP -> renderMapMemberSerializer(memberShape, targetShape as MapShape)
            ShapeType.STRUCTURE,
            ShapeType.UNION -> renderPrimitiveShapeSerializer(memberShape, ::serializerForStructureShape)
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
            ShapeType.BIG_INTEGER -> renderPrimitiveShapeSerializer(memberShape, ::serializerForPrimitiveShape)
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
    open fun renderMapMemberSerializer(memberShape: MemberShape, targetShape: MapShape) {
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
    open fun renderListMemberSerializer(memberShape: MemberShape, targetShape: CollectionShape) {
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
    protected fun delegateMapSerialization(rootMemberShape: MemberShape, mapShape: MapShape, nestingLevel: Int, parentMemberName: String) {
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
            ShapeType.TIMESTAMP -> renderTimestampEntry(mapShape.value, nestingLevel, parentMemberName)
            ShapeType.SET,
            ShapeType.LIST -> renderListEntry(rootMemberShape, elementShape as CollectionShape, nestingLevel, parentMemberName)
            ShapeType.MAP -> renderMapEntry(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE -> renderNestedStructureEntry(elementShape, nestingLevel, parentMemberName, isSparse)
            else -> error("Unhandled type ${elementShape.type}")
        }
    }

    /**
     * Delegates to other functions based on the type of element.
     */
    protected fun delegateListSerialization(rootMemberShape: MemberShape, listShape: CollectionShape, nestingLevel: Int, parentMemberName: String) {
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
            ShapeType.TIMESTAMP -> renderTimestampElement(listShape.member, nestingLevel, parentMemberName)
            ShapeType.LIST,
            ShapeType.SET -> renderListElement(rootMemberShape, elementShape as CollectionShape, nestingLevel, parentMemberName)
            ShapeType.MAP -> renderMapElement(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE -> renderNestedStructureElement(elementShape, nestingLevel, parentMemberName)
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
    private fun renderNestedStructureElement(structureShape: Shape, nestingLevel: Int, parentMemberName: String) {
        val serializerFnName = structureShape.type.primitiveSerializerFunctionName()
        val serializerTypeName = "${structureShape.defaultName()}Serializer"
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.COLLECTION)
        val containerName = if (nestingLevel == 0) "input." else ""
        val valueToSerializeName = valueToSerializeName(elementName)

        writer.withBlock("for ($elementName in $containerName$parentMemberName) {", "}") {
            writer.write("$serializerFnName($serializerTypeName($valueToSerializeName))")
        }
    }

    /**
     * Renders a nested structure contained in a map.  Example:
     *
     * ```
     * input.payload.forEach { (key, value) -> entry(key, FooStructureSerializer(value)) }
     * ```
     */
    private fun renderNestedStructureEntry(
        structureShape: Shape,
        nestingLevel: Int,
        parentMemberName: String,
        isSparse: Boolean
    ) {
        val serializerTypeName = "${structureShape.defaultName()}Serializer"
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val containerName = if (nestingLevel == 0) "input." else ""

        when (isSparse) {
            true -> writer.write("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> if ($valueName != null) entry($keyName, $serializerTypeName($valueName)) else entry($keyName, null as String?) }")
            false -> writer.write("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> entry($keyName, $serializerTypeName($valueName)) }")
        }
    }

    /**
     * Renders the serialization of a list element of type map.
     *
     * Example:
     * ```
     * for (c0 in input.payload) {
     *      serializer.serializeMap(PAYLOAD_C0_DESCRIPTOR) {
     *          ...
     *      }
     * }
     * ```
     */
    open fun renderMapElement(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""
        val parentName = parentName(elementName)

        writer.withBlock("for ($elementName in $containerName$parentMemberName) {", "}") {
            writer.withBlock("serializer.serializeMap($descriptorName) {", "}") {
                delegateMapSerialization(rootMemberShape, mapShape, nestingLevel + 1, parentName)
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
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val parentName = parentName(valueName)

        writer.withBlock("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> mapEntry($keyName, $descriptorName) {", "}}") {
            delegateMapSerialization(rootMemberShape, mapShape, nestingLevel + 1, parentName)
        }
    }

    /**
     * Renders a map value of type list.  Example:
     *
     * ```
     * input.payload.forEach { (key, value) -> listEntry(key, PAYLOAD_C0_DESCRIPTOR) {
     *  ...
     * }}
     * ```
     */
    private fun renderListEntry(
        rootMemberShape: MemberShape,
        elementShape: CollectionShape,
        nestingLevel: Int,
        parentMemberName: String
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val containerName = if (nestingLevel == 0) "input." else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val parentName = parentName(valueName)

        writer.withBlock("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> listEntry($keyName, $descriptorName) {", "}}") {
            delegateListSerialization(rootMemberShape, elementShape, nestingLevel + 1, parentName)
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
    private fun renderListElement(rootMemberShape: MemberShape, elementShape: CollectionShape, nestingLevel: Int, parentListMemberName: String) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$parentListMemberName) {", "}") {
            writer.withBlock("serializer.serializeList($descriptorName) {", "}") {
                delegateListSerialization(rootMemberShape, elementShape, nestingLevel + 1, elementName)
            }
        }
    }

    /**
     * Renders the serialization of a primitive value contained by a map.  Example:
     *
     * ```
     * c0.forEach { (key1, value1) -> entry(key1, value1) }
     * ```
     */
    private fun renderPrimitiveEntry(elementShape: Shape, nestingLevel: Int, listMemberName: String) {
        val containerName = if (nestingLevel == 0) "input." else ""
        val enumPostfix = if (elementShape.isEnum()) ".value" else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)

        writer.write("$containerName$listMemberName.forEach { ($keyName, $valueName) -> entry($keyName, $valueName$enumPostfix) }")
    }

    /**
     * Renders the serialization of a blob value contained by a map.  Example:
     *
     * ```
     * input.fooBlobMap.forEach { (key, value) -> entry(key, value.encodeBase64String()) }
     * ```
     */
    private fun renderBlobEntry(nestingLevel: Int, listMemberName: String) {
        importBase64Utils(writer)

        val containerName = if (nestingLevel == 0) "input." else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)

        writer.write("$containerName$listMemberName.forEach { ($keyName, $valueName) -> entry($keyName, $valueName.encodeBase64String()) }")
    }

    /**
     * Renders the serialization of a timestamp value contained by a map.  Example:
     *
     * ```
     * input.fooTimestampMap.forEach { (key, value) -> rawEntry(key, it.format(TimestampFormat.EPOCH_SECONDS)) }
     * ```
     */
    private fun renderTimestampEntry(memberShape: Shape, nestingLevel: Int, listMemberName: String) {
        importTimestampFormat(writer)
        val tsFormat = memberShape
            .getTrait(TimestampFormatTrait::class.java)
            .map { it.format }
            .orElse(defaultTimestampFormat)

        val (keyName, valueName) = keyValueNames(nestingLevel)
        val serializerFn = when (tsFormat) {
            TimestampFormatTrait.Format.EPOCH_SECONDS -> "rawEntry"
            else -> "entry"
        }

        val encoding = formatInstant("it", tsFormat, forceString = true)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.write("$containerName$listMemberName.forEach { ($keyName, $valueName) -> $serializerFn($keyName, $encoding) }")
    }

    /**
     * Render a List element of a primitive type
     *
     * Example:
     * ```
     * for (m0 in input.payload) {
     *    serializeInt(m0)
     * }
     * ```
     */
    private fun renderPrimitiveElement(
        elementShape: Shape,
        nestingLevel: Int,
        listMemberName: String,
        isSparse: Boolean
    ) {
        val serializerFnName = elementShape.type.primitiveSerializerFunctionName()
        val iteratorName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val elementName = when (elementShape.isEnum()) {
            true -> "$iteratorName.value"
            false -> iteratorName
        }

        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($iteratorName in $containerName$listMemberName) {", "}") {
            when (isSparse) {
                true -> writer.write("if ($elementName != null) $serializerFnName($elementName) else serializeNull()")
                false -> writer.write("$serializerFnName($elementName)")
            }
        }
    }

    /**
     * Render a blob element of a list.  Example:
     *
     * ```
     * for (c0 in input.fooBlobList) {
     *      serializeString(c0.encodeBase64String())
     * }
     */
    private fun renderBlobElement(nestingLevel: Int, listMemberName: String) {
        importBase64Utils(writer)
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$listMemberName) {", "}") {
            writer.write("serializeString($elementName.encodeBase64String())")
        }
    }

    /**
     * Render a timestamp value of a list.  Example:
     *
     * ```
     * for (c0 in input.payload) {
     *      serializeRaw(c0.format(TimestampFormat.EPOCH_SECONDS))
     * }
     */
    private fun renderTimestampElement(memberShape: Shape, nestingLevel: Int, listMemberName: String) {
        // :test(timestamp, member > timestamp)
        importTimestampFormat(writer)
        val tsFormat = memberShape
            .getTrait(TimestampFormatTrait::class.java)
            .map { it.format }
            .orElse(defaultTimestampFormat)

        val serializerFn = when (tsFormat) {
            TimestampFormatTrait.Format.EPOCH_SECONDS -> "serializeRaw"
            else -> "serializeString"
        }

        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""
        val encoding = formatInstant(elementName, tsFormat, forceString = true)

        writer.withBlock("for ($elementName in $containerName$listMemberName) {", "}") {
            writer.write("$serializerFn($encoding)")
        }
    }

    private fun renderDocumentShapeSerializer(memberShape: MemberShape) {
        // TODO("Not yet implemented")
    }

    /**
     * Render code to serialize a primitive or structure shape. Example:
     *
     * ```
     * input.payload?.let { field(PAYLOAD_DESCRIPTOR, it) }
     * ```
     *
     * @param memberShape [MemberShape] referencing the primitive type
     */
    open fun renderPrimitiveShapeSerializer(memberShape: MemberShape, serializerNameFn: (MemberShape) -> SerializeInfo) {
        val (serializeFn, encoded) = serializerNameFn(memberShape)
        // FIXME - this doesn't account for unboxed primitives
        val postfix = idempotencyTokenPostfix(memberShape)

        writer.write("input.\$L?.let { $serializeFn(\$L, $encoded) }$postfix", memberShape.defaultName(), memberShape.descriptorName())
    }

    /**
     * Return string to postfix to serializer for idempotency generation
     * @param memberShape shape which would have the IdempotencyTokenTrait
     * @return string intended for codegen output
     */
    private fun idempotencyTokenPostfix(memberShape: MemberShape): String =
        if (memberShape.hasTrait(IdempotencyTokenTrait::class.java)) {
            " ?: field(${memberShape.descriptorName()}, serializationContext.idempotencyTokenProvider.generateToken())"
        } else {
            ""
        }

    /**
     * Return the serializer function for a Structure or Union
     * @param shape [Shape] to generate serializer
     * @return SerializeInfo of field name and encoding
     */
    private fun serializerForStructureShape(shape: Shape): SerializeInfo {
        // target shape type to deserialize is either the shape itself or member.target
        val target = shape.targetOrSelf(ctx.model)
        // the Smithy type hierarchy is private such that tighter type handling at the function level isn't possible
        require(target.type == ShapeType.STRUCTURE || target.type == ShapeType.UNION) { "Unexpected serializer for member: $shape; target: $target" }

        val symbol = ctx.symbolProvider.toSymbol(target)
        val memberSerializerName = "${symbol.name}Serializer"
        val valueToSerializeName = valueToSerializeName("it")
        // invoke the ctor of the serializer to delegate to and pass the value
        val encoded = "$memberSerializerName($valueToSerializeName)"

        return SerializeInfo("field", encoded)
    }

    /**
     * get the serialization function and encoded value for the given [Shape], this only handles "primitive" types,
     * collections should be handled separately
     * @param shape The shape to be serialized
     */
    private fun serializerForPrimitiveShape(shape: Shape): SerializeInfo {
        // target shape type to deserialize is either the shape itself or member.target
        val target = shape.targetOrSelf(ctx.model)
        val defaultIdentifier = valueToSerializeName("it")
        var serializerFn = "field"

        val encoded = when (target.type) {
            ShapeType.BOOLEAN,
            ShapeType.BYTE,
            ShapeType.SHORT,
            ShapeType.INTEGER,
            ShapeType.LONG,
            ShapeType.FLOAT,
            ShapeType.DOUBLE -> defaultIdentifier
            ShapeType.BLOB -> {
                importBase64Utils(writer)
                "$defaultIdentifier.encodeBase64String()"
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

                formatInstant(defaultIdentifier, tsFormat, forceString = true)
            }
            ShapeType.STRING -> when {
                target.hasTrait(EnumTrait::class.java) -> "$defaultIdentifier.value"
                else -> defaultIdentifier
            }
            else -> throw CodegenException("unknown serializer for member: $shape; target: $target")
        }

        return SerializeInfo(serializerFn, encoded)
    }

    /**
     * @return true if shape is a String with enum trait, false otherwise.
     */
    private fun Shape.isEnum() = isStringShape && hasTrait(EnumTrait::class.java)

    /**
     * Generate key and value names for iteration based on nesting level
     * @param nestingLevel current level of nesting
     * @return key and value as a pair of strings
     */
    private fun keyValueNames(nestingLevel: Int): Pair<String, String> {
        val keyName = if (nestingLevel == 0) "key" else "key$nestingLevel"
        val valueName = if (nestingLevel == 0) "value" else "value$nestingLevel"

        return keyName to valueName
    }

    /**
     * Get the name of the `PrimitiveSerializer` function name for the corresponding shape type
     * @throws CodegenException when no known function name for the given type is known to exist
     */
    private fun ShapeType.primitiveSerializerFunctionName(): String {
        val suffix = when (this) {
            ShapeType.BOOLEAN -> "Boolean"
            ShapeType.STRING -> "String"
            ShapeType.BYTE -> "Byte"
            ShapeType.SHORT -> "Short"
            ShapeType.INTEGER -> "Int"
            ShapeType.LONG -> "Long"
            ShapeType.FLOAT -> "Float"
            ShapeType.DOUBLE -> "Double"
            ShapeType.STRUCTURE, ShapeType.UNION -> "SdkSerializable"
            else -> throw CodegenException("$this has no primitive serialize function on the Serializer interface")
        }
        return "serialize$suffix"
    }
}
