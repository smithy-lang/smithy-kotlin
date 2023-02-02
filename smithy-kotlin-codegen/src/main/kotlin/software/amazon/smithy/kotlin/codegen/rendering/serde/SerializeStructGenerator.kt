/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.model.targetOrSelf
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*

/**
 * Generate serialization for members bound to the payload.
 *
 * NOTE: If the serialization order is important then [members] MUST already be sorted correctly
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
    // FIXME - refactor to just take a CodegenContext rather than the more specific protocol generator context. Serde should be protocol agnostic (ideally)
    protected val ctx: ProtocolGenerator.GenerationContext,
    protected val members: List<MemberShape>,
    protected val writer: KotlinWriter,
    protected val defaultTimestampFormat: TimestampFormatTrait.Format,
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
        val objDescriptor = if (members.isNotEmpty()) {
            "OBJ_DESCRIPTOR"
        } else {
            writer.addImport(RuntimeTypes.Serde.SdkObjectDescriptor)
            "SdkObjectDescriptor.build{}"
        }
        writer.withBlock("serializer.#T($objDescriptor) {", "}", RuntimeTypes.Serde.serializeStruct) {
            members.forEach { memberShape ->
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
            ShapeType.SET,
            -> renderListMemberSerializer(memberShape, targetShape as CollectionShape)

            ShapeType.MAP -> renderMapMemberSerializer(memberShape, targetShape as MapShape)
            ShapeType.STRUCTURE,
            ShapeType.UNION,
            -> renderPrimitiveShapeSerializer(memberShape, ::serializerForStructureShape)

            ShapeType.TIMESTAMP -> renderTimestampMemberSerializer(memberShape)
            ShapeType.BLOB,
            ShapeType.BOOLEAN,
            ShapeType.STRING,
            ShapeType.BYTE,
            ShapeType.SHORT,
            ShapeType.INTEGER,
            ShapeType.LONG,
            ShapeType.FLOAT,
            ShapeType.DOUBLE,
            ShapeType.BIG_DECIMAL,
            ShapeType.DOCUMENT,
            ShapeType.BIG_INTEGER,
            ShapeType.ENUM,
            -> renderPrimitiveShapeSerializer(memberShape, ::serializerForPrimitiveShape)

            ShapeType.INT_ENUM -> error("IntEnum is not supported until Smithy 2.0")

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
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
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
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
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
        val isSparse = mapShape.isSparse

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
            ShapeType.DOCUMENT,
            ShapeType.BIG_INTEGER,
            ShapeType.ENUM,
            -> renderPrimitiveEntry(elementShape, nestingLevel, parentMemberName)

            ShapeType.BLOB -> renderBlobEntry(nestingLevel, parentMemberName)
            ShapeType.TIMESTAMP -> renderTimestampEntry(mapShape.value, elementShape, nestingLevel, parentMemberName)
            ShapeType.SET,
            ShapeType.LIST,
            -> renderListEntry(rootMemberShape, elementShape as CollectionShape, nestingLevel, isSparse, parentMemberName)

            ShapeType.MAP -> renderMapEntry(rootMemberShape, elementShape as MapShape, nestingLevel, isSparse, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE,
            -> renderNestedStructureEntry(elementShape, nestingLevel, parentMemberName, isSparse)

            ShapeType.INT_ENUM -> error("IntEnum is not supported until Smithy 2.0")

            else -> error("Unhandled type ${elementShape.type}")
        }
    }

    /**
     * Delegates to other functions based on the type of element.
     */
    protected fun delegateListSerialization(rootMemberShape: MemberShape, listShape: CollectionShape, nestingLevel: Int, parentMemberName: String) {
        val elementShape = ctx.model.expectShape(listShape.member.target)
        val isSparse = listShape.isSparse

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
            ShapeType.DOCUMENT,
            ShapeType.BIG_INTEGER,
            ShapeType.ENUM,
            -> renderPrimitiveElement(elementShape, nestingLevel, parentMemberName, isSparse)

            ShapeType.BLOB -> renderBlobElement(nestingLevel, parentMemberName)
            ShapeType.TIMESTAMP -> renderTimestampElement(listShape.member, elementShape, nestingLevel, parentMemberName)
            ShapeType.LIST,
            ShapeType.SET,
            -> renderListElement(rootMemberShape, elementShape as CollectionShape, nestingLevel, parentMemberName)

            ShapeType.MAP -> renderMapElement(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE,
            -> renderNestedStructureElement(elementShape, nestingLevel, parentMemberName)

            ShapeType.INT_ENUM -> error("IntEnum is not supported until Smithy 2.0")

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
        val serializerTypeName = ctx.symbolProvider.toSymbol(structureShape).documentSerializerName()
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""
        val valueToSerializeName = valueToSerializeName(elementName)

        writer.withBlock("for ($elementName in $containerName$parentMemberName) {", "}") {
            writer.write("$serializerFnName(#T($valueToSerializeName, ::$serializerTypeName))", RuntimeTypes.Serde.asSdkSerializable)
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
        isSparse: Boolean,
    ) {
        val serializerTypeName = ctx.symbolProvider.toSymbol(structureShape).documentSerializerName()
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val containerName = if (nestingLevel == 0) "input." else ""

        val value = "asSdkSerializable($valueName, ::$serializerTypeName)"

        when (isSparse) {
            true -> writer.write("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> if ($valueName != null) entry($keyName, $value) else entry($keyName, null as String?) }")
            false -> writer.write("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> entry($keyName, $value) }")
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
        parentMemberName: String,
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""
        val parentName = parentName(elementName)

        writer.withBlock("for ($elementName in $containerName$parentMemberName) {", "}") {
            writer.withBlock("serializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.serializeMap) {
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
        isSparse: Boolean,
        parentMemberName: String,
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val containerName = if (nestingLevel == 0) "input." else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val parentName = parentName(valueName)

        writer.withBlock("$containerName$parentMemberName.forEach { ($keyName, $valueName) ->", "}") {
            writer.wrapBlockIf(isSparse, "if ($valueName != null) {", "} else entry($keyName, null as String?)") {
                writer.withBlock("mapEntry($keyName, $descriptorName) {", "}") {
                    delegateMapSerialization(rootMemberShape, mapShape, nestingLevel + 1, parentName)
                }
            }
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
        isSparse: Boolean,
        parentMemberName: String,
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val containerName = if (nestingLevel == 0) "input." else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val parentName = parentName(valueName)

        writer.withBlock("$containerName$parentMemberName.forEach { ($keyName, $valueName) ->", "}") {
            writer.wrapBlockIf(isSparse, "if ($valueName != null) {", "} else entry($keyName, null as String?)") {
                writer.withBlock("listEntry($keyName, $descriptorName) {", "}") {
                    delegateListSerialization(rootMemberShape, elementShape, nestingLevel + 1, parentName)
                }
            }
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
            writer.withBlock("serializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.serializeList) {
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
        val enumPostfix = if (elementShape.isEnum) ".value" else ""
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
        val containerName = if (nestingLevel == 0) "input." else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)

        writer.write(
            "$containerName$listMemberName.forEach { ($keyName, $valueName) -> entry($keyName, $valueName.#T()) }",
            RuntimeTypes.Core.Utils.encodeBase64String,
        )
    }

    /**
     * Renders the serialization of a timestamp value contained by a map.  Example:
     *
     * ```
     * input.fooTimestampMap.forEach { (key, value) -> entry(key, it, TimestampFormat.EPOCH_SECONDS) }
     * ```
     */
    private fun renderTimestampEntry(memberShape: Shape, elementShape: Shape, nestingLevel: Int, listMemberName: String) {
        writer.addImport(RuntimeTypes.Core.TimestampFormat)

        // favor the member shape if it overrides the value shape trait
        val shape = if (memberShape.hasTrait<TimestampFormatTrait>()) {
            memberShape
        } else {
            elementShape
        }

        val tsFormat = shape
            .getTrait(TimestampFormatTrait::class.java)
            .map { it.format }
            .orElse(defaultTimestampFormat)
            .toRuntimeEnum()

        val (keyName, valueName) = keyValueNames(nestingLevel)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.write("$containerName$listMemberName.forEach { ($keyName, $valueName) -> entry($keyName, it, $tsFormat) }")
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
        isSparse: Boolean,
    ) {
        val serializerFnName = elementShape.type.primitiveSerializerFunctionName()
        val iteratorName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val elementName = when (elementShape.isEnum) {
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
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$listMemberName) {", "}") {
            writer.write("serializeString($elementName.#T())", RuntimeTypes.Core.Utils.encodeBase64String)
        }
    }

    /**
     * Render a timestamp value of a list.  Example:
     *
     * ```
     * for (c0 in input.payload) {
     *      serializeInstant(c0, TimestampFormat.EPOCH_SECONDS)
     * }
     */
    private fun renderTimestampElement(memberShape: Shape, elementShape: Shape, nestingLevel: Int, listMemberName: String) {
        // :test(timestamp, member > timestamp)
        writer.addImport(RuntimeTypes.Core.TimestampFormat)

        // favor the member shape if it overrides the value shape trait
        val shape = if (memberShape.hasTrait<TimestampFormatTrait>()) {
            memberShape
        } else {
            elementShape
        }

        val tsFormat = shape
            .getTrait(TimestampFormatTrait::class.java)
            .map { it.format }
            .orElse(defaultTimestampFormat)
            .toRuntimeEnum()

        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$listMemberName) {", "}") {
            writer.write("serializeInstant($elementName, $tsFormat)")
        }
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
        val postfix = idempotencyTokenPostfix(memberShape)

        val targetShape = ctx.model.expectShape(memberShape.target)
        val targetSymbol = ctx.symbolProvider.toSymbol(memberShape)
        val defaultValue = targetSymbol.defaultValue()
        val memberName = ctx.symbolProvider.toMemberName(memberShape)

        if ((targetShape.isNumberShape || targetShape.isBooleanShape) && targetSymbol.isNotBoxed && defaultValue != null) {
            // unboxed primitive with a default value
            val ident = "input.$memberName"
            val check = when (memberShape.hasTrait<RequiredTrait>()) {
                true -> "" // always serialize a required member even if it's the default
                else -> "if ($ident != $defaultValue) "
            }
            writer.write("${check}$serializeFn(#L, $ident)", memberShape.descriptorName())
        } else {
            writer.write("input.#L?.let { $serializeFn(#L, $encoded) }$postfix", memberName, memberShape.descriptorName())
        }
    }

    open fun renderTimestampMemberSerializer(memberShape: MemberShape) {
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        writer.addImport(RuntimeTypes.Core.TimestampFormat)
        val target = ctx.model.expectShape(memberShape.target)
        val tsFormat = memberShape
            .getTrait(TimestampFormatTrait::class.java)
            .map { it.format }
            .orElseGet {
                target.getTrait(TimestampFormatTrait::class.java)
                    .map { it.format }
                    .orElse(defaultTimestampFormat)
            }
            .toRuntimeEnum()

        writer.write("input.#L?.let { field(#L, it, #L) }", memberName, memberShape.descriptorName(), tsFormat)
    }

    /**
     * Return string to postfix to serializer for idempotency generation
     * @param memberShape shape which would have the IdempotencyTokenTrait
     * @return string intended for codegen output
     */
    private fun idempotencyTokenPostfix(memberShape: MemberShape): String =
        if (memberShape.hasTrait<IdempotencyTokenTrait>()) {
            writer.addImport(RuntimeTypes.Core.Client.IdempotencyTokenProviderExt)
            " ?: field(${memberShape.descriptorName()}, context.idempotencyTokenProvider.generateToken())"
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
        val memberSerializerName = symbol.documentSerializerName()
        val valueToSerializeName = valueToSerializeName("it")
        // invoke the ctor of the serializer to delegate to and pass the value
        val encoded = "$valueToSerializeName, ::$memberSerializerName"

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
        val valueSuffix = if (target.isEnum) ".value" else ""
        val defaultIdentifier = valueToSerializeName("it") + valueSuffix
        val serializerFn = "field"

        val encoded = if (target.type == ShapeType.BLOB) {
            writer.addImport(RuntimeTypes.Core.Utils.encodeBase64String)
            "$defaultIdentifier.encodeBase64String()"
        } else {
            defaultIdentifier
        }

        return SerializeInfo(serializerFn, encoded)
    }

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
            ShapeType.STRING, ShapeType.ENUM -> "String"
            ShapeType.BYTE -> "Byte"
            ShapeType.SHORT -> "Short"
            ShapeType.INTEGER, ShapeType.INT_ENUM -> "Int"
            ShapeType.LONG -> "Long"
            ShapeType.FLOAT -> "Float"
            ShapeType.DOUBLE -> "Double"
            ShapeType.DOCUMENT -> "Document"
            ShapeType.STRUCTURE, ShapeType.UNION -> "SdkSerializable"
            else -> throw CodegenException("$this has no primitive serialize function on the Serializer interface")
        }
        return "serialize$suffix"
    }
}

fun TimestampFormatTrait.Format.toRuntimeEnum(): String = when (this) {
    TimestampFormatTrait.Format.EPOCH_SECONDS -> "TimestampFormat.EPOCH_SECONDS"
    TimestampFormatTrait.Format.DATE_TIME -> "TimestampFormat.ISO_8601"
    TimestampFormatTrait.Format.HTTP_DATE -> "TimestampFormat.RFC_5322"
    else -> throw CodegenException("unknown timestamp format: $this")
}
