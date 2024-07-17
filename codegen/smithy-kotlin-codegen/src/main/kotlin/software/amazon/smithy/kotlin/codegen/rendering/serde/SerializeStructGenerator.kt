/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.DefaultValueSerializationMode
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
     */
    fun interface SerializeFunction {
        /**
         * Return the formatted function invocation to serialize the given [member]
         * e.g.
         * ```
         * field(X_DESCRIPTOR, input.x)
         * ```
         * @param member the member shape being serialized
         * @param identifier the identifier name to render the invocation with
         */
        fun format(member: MemberShape, identifier: String): String
    }

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
            -> renderShapeSerializer(memberShape, serializerForStructureShape)

            ShapeType.BLOB,
            ShapeType.BOOLEAN,
            ShapeType.STRING,
            ShapeType.BYTE,
            ShapeType.SHORT,
            ShapeType.INTEGER,
            ShapeType.LONG,
            ShapeType.FLOAT,
            ShapeType.DOUBLE,
            ShapeType.BIG_INTEGER,
            ShapeType.BIG_DECIMAL,
            ShapeType.TIMESTAMP,
            ShapeType.DOCUMENT,
            ShapeType.ENUM,
            ShapeType.INT_ENUM,
            -> renderShapeSerializer(memberShape, serializerForSimpleShape)

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
        val memberSymbol = ctx.symbolProvider.toSymbol(memberShape)

        writer.wrapBlockIf(memberSymbol.isNullable, "if (input.$memberName != null) {", "}") {
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
        val memberSymbol = ctx.symbolProvider.toSymbol(memberShape)

        writer.wrapBlockIf(memberSymbol.isNullable, "if (input.$memberName != null) {", "}") {
            writer.withBlock("listField($descriptorName) {", "}") {
                delegateListSerialization(memberShape, targetShape, nestingLevel, memberName)
            }
        }
    }

    /**
     * Delegates to other functions based on the type of value target of map.
     */
    protected fun delegateMapSerialization(rootMemberShape: MemberShape, mapShape: MapShape, nestingLevel: Int, parentMemberName: String) {
        val keyShape = ctx.model.expectShape(mapShape.key.target)
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
            ShapeType.INT_ENUM,
            ShapeType.BLOB,
            -> renderPrimitiveEntry(keyShape, elementShape, nestingLevel, parentMemberName, isSparse)

            ShapeType.TIMESTAMP -> renderTimestampEntry(
                keyShape,
                mapShape.value,
                elementShape,
                nestingLevel,
                parentMemberName,
                isSparse,
            )

            ShapeType.SET,
            ShapeType.LIST,
            -> renderListEntry(
                rootMemberShape,
                keyShape,
                elementShape as CollectionShape,
                nestingLevel,
                isSparse,
                parentMemberName,
            )

            ShapeType.MAP -> renderMapEntry(
                rootMemberShape,
                keyShape,
                elementShape as MapShape,
                nestingLevel,
                isSparse,
                parentMemberName,
            )

            ShapeType.UNION,
            ShapeType.STRUCTURE,
            -> renderNestedStructureEntry(keyShape, elementShape, nestingLevel, parentMemberName, isSparse)

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
            ShapeType.INT_ENUM,
            ShapeType.BLOB,
            -> renderPrimitiveElement(elementShape, nestingLevel, parentMemberName, isSparse)

            ShapeType.TIMESTAMP -> renderTimestampElement(listShape.member, elementShape, nestingLevel, parentMemberName, isSparse)
            ShapeType.LIST,
            ShapeType.SET,
            -> renderListElement(rootMemberShape, elementShape as CollectionShape, nestingLevel, parentMemberName, isSparse)

            ShapeType.MAP -> renderMapElement(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName, isSparse)
            ShapeType.UNION,
            ShapeType.STRUCTURE,
            -> renderNestedStructureElement(elementShape, nestingLevel, parentMemberName, isSparse)

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
    private fun renderNestedStructureElement(structureShape: Shape, nestingLevel: Int, parentMemberName: String, isSparse: Boolean) {
        val serializerFnName = structureShape.type.primitiveSerializerFunctionName()
        val serializerTypeName = ctx.symbolProvider.toSymbol(structureShape).documentSerializerName()
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""
        val valueToSerializeName = valueToSerializeName(elementName)

        writer.withBlock("for ($elementName in $containerName$parentMemberName) {", "}") {
            writer.wrapBlockIf(isSparse, "if ($elementName != null) {", "} else serializeNull()") {
                writer.write("$serializerFnName(#T($valueToSerializeName, ::$serializerTypeName))", RuntimeTypes.Serde.asSdkSerializable)
            }
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
        keyShape: Shape,
        structureShape: Shape,
        nestingLevel: Int,
        parentMemberName: String,
        isSparse: Boolean,
    ) {
        val serializerTypeName = ctx.symbolProvider.toSymbol(structureShape).documentSerializerName()
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val keyValue = keyValue(keyShape, keyName)
        val containerName = if (nestingLevel == 0) "input." else ""

        val value = "asSdkSerializable($valueName, ::$serializerTypeName)"

        when (isSparse) {
            true -> writer.write("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> if ($valueName != null) entry($keyValue, $value) else entry($keyValue, null as String?) }")
            false -> writer.write("$containerName$parentMemberName.forEach { ($keyName, $valueName) -> entry($keyValue, $value) }")
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
        isSparse: Boolean,
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""
        val parentName = parentName(elementName)

        writer.withBlock("for ($elementName in $containerName$parentMemberName) {", "}") {
            writer.withBlock("serializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.serializeMap) {
                writer.wrapBlockIf(isSparse, "if ($elementName != null) {", "} else serializeNull()") {
                    delegateMapSerialization(rootMemberShape, mapShape, nestingLevel + 1, parentName)
                }
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
        keyShape: Shape,
        mapShape: MapShape,
        nestingLevel: Int,
        isSparse: Boolean,
        parentMemberName: String,
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val containerName = if (nestingLevel == 0) "input." else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val keyValue = keyValue(keyShape, keyName)
        val parentName = parentName(valueName)

        writer.withBlock("$containerName$parentMemberName.forEach { ($keyName, $valueName) ->", "}") {
            writer.wrapBlockIf(isSparse, "if ($valueName != null) {", "} else entry($keyValue, null as String?)") {
                writer.withBlock("mapEntry($keyValue, $descriptorName) {", "}") {
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
        keyShape: Shape,
        elementShape: CollectionShape,
        nestingLevel: Int,
        isSparse: Boolean,
        parentMemberName: String,
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val containerName = if (nestingLevel == 0) "input." else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val parentName = parentName(valueName)
        val keyValue = keyValue(keyShape, keyName)

        writer.withBlock("$containerName$parentMemberName.forEach { ($keyName, $valueName) ->", "}") {
            writer.wrapBlockIf(isSparse, "if ($valueName != null) {", "} else entry($keyValue, null as String?)") {
                writer.withBlock("listEntry($keyValue, $descriptorName) {", "}") {
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
    private fun renderListElement(rootMemberShape: MemberShape, elementShape: CollectionShape, nestingLevel: Int, parentListMemberName: String, isSparse: Boolean) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$parentListMemberName) {", "}") {
            writer.withBlock("serializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.serializeList) {
                writer.wrapBlockIf(isSparse, "if ($elementName != null) {", "} else serializeNull()") {
                    delegateListSerialization(rootMemberShape, elementShape, nestingLevel + 1, elementName)
                }
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
    private fun renderPrimitiveEntry(keyShape: Shape, elementShape: Shape, nestingLevel: Int, listMemberName: String, isSparse: Boolean) {
        val containerName = if (nestingLevel == 0) "input." else ""
        val enumPostfix = if (elementShape.isEnum) ".value" else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val keyValue = keyValue(keyShape, keyName)

        writer.withBlock("$containerName$listMemberName.forEach { ($keyName, $valueName) ->", "}") {
            writer.wrapBlockIf(isSparse, "if ($valueName != null) {", "} else entry($keyValue, null as String?)") {
                writer.write("entry($keyValue, $valueName$enumPostfix)")
            }
        }
    }

    /**
     * Renders the serialization of a timestamp value contained by a map.  Example:
     *
     * ```
     * input.fooTimestampMap.forEach { (key, value) -> entry(key, it, TimestampFormat.EPOCH_SECONDS) }
     * ```
     */
    private fun renderTimestampEntry(
        keyShape: Shape,
        memberShape: Shape,
        elementShape: Shape,
        nestingLevel: Int,
        listMemberName: String,
        isSparse: Boolean,
    ) {
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
        val keyValue = keyValue(keyShape, keyName)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("$containerName$listMemberName.forEach { ($keyName, $valueName) ->", "}") {
            writer.wrapBlockIf(isSparse, "if ($valueName != null) {", "} else entry($keyValue, null as String?)") {
                writer.write("entry($keyValue, it, $tsFormat)")
            }
        }
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
     * Render a timestamp value of a list.  Example:
     *
     * ```
     * for (c0 in input.payload) {
     *      serializeInstant(c0, TimestampFormat.EPOCH_SECONDS)
     * }
     */
    private fun renderTimestampElement(memberShape: Shape, elementShape: Shape, nestingLevel: Int, listMemberName: String, isSparse: Boolean) {
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
            writer.wrapBlockIf(isSparse, "if ($elementName != null) {", "} else serializeNull()") {
                writer.write("serializeInstant($elementName, $tsFormat)")
            }
        }
    }

    /**
     * Render code to serialize a simple shape or structure shape. Example:
     *
     * ```
     * input.payload?.let { field(PAYLOAD_DESCRIPTOR, it) }
     * ```
     *
     * @param memberShape [MemberShape] member shape to render serializer for
     * @param serializerFn [SerializeFunction] the serializer responsible for returning the function to invoke
     */
    open fun renderShapeSerializer(memberShape: MemberShape, serializerFn: SerializeFunction) {
        val postfix = idempotencyTokenPostfix(memberShape)
        val memberSymbol = ctx.symbolProvider.toSymbol(memberShape)
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        if (memberSymbol.isNullable) {
            val identifier = valueToSerializeName("it")
            val fn = serializerFn.format(memberShape, identifier)
            writer.write("input.$memberName?.let { $fn }$postfix")
        } else {
            // always serialize required members, otherwise check if it's a primitive type set to it's default before serializing
            val defaultValue = memberSymbol.defaultValue()
            val checkDefaults = ctx.settings.api.defaultValueSerializationMode == DefaultValueSerializationMode.WHEN_DIFFERENT
            val defaultCheck = if (checkDefaults && !memberShape.isRequired && memberSymbol.isNotNullable && defaultValue != null) {
                "if (input.$memberName != $defaultValue) "
            } else {
                ""
            }
            val fn = serializerFn.format(memberShape, "input.$memberName")
            writer.write("$defaultCheck$fn$postfix")
        }
    }

    /**
     * Return string to postfix to serializer for idempotency generation
     * @param memberShape shape which would have the IdempotencyTokenTrait
     * @return string intended for codegen output
     */
    private fun idempotencyTokenPostfix(memberShape: MemberShape): String =
        if (memberShape.hasTrait<IdempotencyTokenTrait>()) {
            writer.addImport(RuntimeTypes.SmithyClient.IdempotencyTokenProviderExt)
            " ?: field(${memberShape.descriptorName()}, context.idempotencyTokenProvider.generateToken())"
        } else {
            ""
        }

    /**
     * Return the serializer function for a Structure or Union
     */
    private val serializerForStructureShape: SerializeFunction =
        SerializeFunction { member, identifier ->
            // target shape type to deserialize is either the shape itself or member.target
            val target = member.targetOrSelf(ctx.model)
            // the Smithy type hierarchy is private such that tighter type handling at the function level isn't possible
            require(target.type == ShapeType.STRUCTURE || target.type == ShapeType.UNION) { "Unexpected serializer for member: $member; target: $target" }

            val symbol = ctx.symbolProvider.toSymbol(target)
            val memberSerializerName = symbol.documentSerializerName()
            val descriptor = member.descriptorName()
            // invoke the ctor of the serializer to delegate to and pass the value
            "field($descriptor, $identifier, ::$memberSerializerName)"
        }

    /**
     * Get the serialization function and encoded value for the given [Shape], this only handles
     * [simple types](https://smithy.io/2.0/spec/simple-types.html),  collections should be handled separately.
     */
    protected val serializerForSimpleShape = SerializeFunction { member, identifier ->
        // target shape type to deserialize is either the shape itself or member.target
        val target = member.targetOrSelf(ctx.model)

        val encoded = when {
            target.type == ShapeType.BLOB -> writer.format("#L.#T()", identifier, RuntimeTypes.Core.Text.Encoding.encodeBase64String)
            target.type == ShapeType.TIMESTAMP -> {
                writer.addImport(RuntimeTypes.Core.TimestampFormat)
                val tsFormat = member
                    .getTrait(TimestampFormatTrait::class.java)
                    .map { it.format }
                    .orElseGet {
                        target.getTrait(TimestampFormatTrait::class.java)
                            .map { it.format }
                            .orElse(defaultTimestampFormat)
                    }
                    .toRuntimeEnum()
                "$identifier, $tsFormat"
            }
            target.isEnum -> "$identifier.value"
            else -> identifier
        }

        val descriptor = member.descriptorName()
        "field($descriptor, $encoded)"
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

    private fun keyValue(keyShape: Shape, keyName: String) = keyName + if (keyShape.isEnum) ".value" else ""

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
            ShapeType.BLOB -> "ByteArray"
            else -> throw CodegenException("$this has no primitive serialize function on the Serializer interface")
        }
        return "serialize$suffix"
    }
}
