/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Generate deserialization for members bound to the payload.
 *
 * e.g.
 * ```
 * deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
 *    loop@while (true) {
 *        when (findNextFieldIndex()) {
 *             FIELD1_DESCRIPTOR.index -> builder.field1 = deserializeString()
 *             FIELD2_DESCRIPTOR.index -> builder.field2 = deserializeInt()
 *             null -> break@loop
 *             else -> skipValue()
 *         }
 *     }
 * }
 * ```
 */
open class DeserializeStructGenerator(
    protected val ctx: ProtocolGenerator.GenerationContext,
    protected val members: List<MemberShape>,
    protected val writer: KotlinWriter,
    protected val defaultTimestampFormat: TimestampFormatTrait.Format,
) {
    /**
     * Enables overriding the codegen output of the final value resulting
     * from the deserialization of a non-primitive type.
     * @param memberShape [MemberShape] associated with entry
     * @param defaultCollectionName the default value produced by this class.
     */
    open fun collectionReturnExpression(memberShape: MemberShape, defaultCollectionName: String): String = defaultCollectionName

    /**
     * Enables overriding of the lhs expression into which a deserialization operation's
     * result is saved.
     */
    open fun deserializationResultName(defaultName: String): String = defaultName

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
            "SdkObjectDescriptor.build {}"
        }
        writer.withBlock("deserializer.#T($objDescriptor) {", "}", RuntimeTypes.Serde.deserializeStruct) {
            withBlock("loop@while (true) {", "}") {
                withBlock("when (findNextFieldIndex()) {", "}") {
                    members.sortedBy { it.memberName }.forEach { memberShape ->
                        renderMemberShape(memberShape)
                    }
                    write("null -> break@loop")
                    write("else -> skipValue()")
                }
            }
        }
    }

    /**
     * Deserialize top-level members.
     */
    protected open fun renderMemberShape(memberShape: MemberShape) {
        val targetShape = ctx.model.expectShape(memberShape.target)

        when (targetShape.type) {
            ShapeType.LIST,
            ShapeType.SET,
            -> renderListMemberDeserializer(memberShape, targetShape as CollectionShape)

            ShapeType.MAP -> renderMapMemberDeserializer(memberShape, targetShape as MapShape)

            ShapeType.STRUCTURE,
            ShapeType.UNION,
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
            ShapeType.DOCUMENT,
            ShapeType.BIG_DECIMAL,
            ShapeType.BIG_INTEGER,
            ShapeType.ENUM,
            -> renderShapeDeserializer(memberShape)

            ShapeType.INT_ENUM -> error("IntEnum is not supported until Smithy 2.0")

            else -> error("Unexpected shape type: ${targetShape.type}")
        }
    }

    /**
     * Codegen the deserialization of a primitive value into a response type. Example:
     * ```
     * PAYLOAD_DESCRIPTOR.index -> builder.payload = deserializeString().let { Instant.fromEpochSeconds(it) }
     * ```
     */
    open fun renderShapeDeserializer(memberShape: MemberShape) {
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        val descriptorName = memberShape.descriptorName()
        val deserialize = deserializerForShape(memberShape)

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
    protected fun renderMapMemberDeserializer(memberShape: MemberShape, targetShape: MapShape) {
        val nestingLevel = 0
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        val descriptorName = memberShape.descriptorName()
        val mutableCollectionType = targetShape.mutableCollectionType()
        val valueCollector = deserializationResultName("builder.$memberName")
        val mutableCollectionName = nestingLevel.variableNameFor(NestedIdentifierType.MAP)
        val collectionReturnExpression = collectionReturnExpression(memberShape, mutableCollectionName)

        writer.write("$descriptorName.index -> $valueCollector = ")
            .indent()
            .withBlock("deserializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.deserializeMap) {
                write("val $mutableCollectionName = $mutableCollectionType()")
                withBlock("while (hasNextEntry()) {", "}") {
                    delegateMapDeserialization(memberShape, targetShape, nestingLevel, mutableCollectionName)
                }
                write(collectionReturnExpression)
            }
            .dedent()
    }

    /**
     * Delegates to other functions based on the type of value target of map.
     */
    private fun delegateMapDeserialization(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMemberName: String,
    ) {
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
            ShapeType.BIG_INTEGER,
            ShapeType.BLOB,
            ShapeType.DOCUMENT,
            ShapeType.TIMESTAMP,
            ShapeType.ENUM,
            -> renderEntry(elementShape, nestingLevel, isSparse, parentMemberName)

            ShapeType.SET,
            ShapeType.LIST,
            -> renderListEntry(rootMemberShape, elementShape as CollectionShape, nestingLevel, isSparse, parentMemberName)

            ShapeType.MAP -> renderMapEntry(rootMemberShape, elementShape as MapShape, nestingLevel, isSparse, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE,
            -> renderNestedStructureEntry(elementShape, nestingLevel, isSparse, parentMemberName)

            ShapeType.INT_ENUM -> error("IntEnum is not supported until Smithy 2.0")

            else -> error("Unhandled type ${elementShape.type}")
        }
    }

    /**
     * Renders the deserialization of a nested structure contained in a map.  Example:
     *
     * ```
     * val k0 = key()
     * val v0 = if (nextHasValue()) { deserializeString().let { Instant.fromEpochSeconds(it) } } else { deserializeNull(); continue }
     * map0[k0] = v0
     * ```
     */
    private fun renderNestedStructureEntry(
        elementShape: Shape,
        nestingLevel: Int,
        isSparse: Boolean,
        parentMemberName: String,
    ) {
        val deserializerFn = deserializerForShape(elementShape)
        val keyName = nestingLevel.variableNameFor(NestedIdentifierType.KEY)
        val valueName = nestingLevel.variableNameFor(NestedIdentifierType.VALUE)
        val populateNullValuePostfix = if (isSparse) "" else "; continue"
        if (elementShape.isStructureShape || elementShape.isUnionShape) {
            val symbol = ctx.symbolProvider.toSymbol(elementShape)
            writer.addImport(symbol)
        }

        writer.write("val $keyName = key()")
        writer.write("val $valueName = if (nextHasValue()) { $deserializerFn } else { deserializeNull()$populateNullValuePostfix }")
        writer.write("$parentMemberName[$keyName] = $valueName")
    }

    /**
     * Render the deserialization of a map entry.  Example:
     * ```
     * val k0 = key()
     * val v0 = deserializer.deserializeMap(PAYLOAD_C0_DESCRIPTOR) {
     *      val m1 = mutableMapOf<String, Int>()
     *      while (hasNextEntry()) {
     *           ...
     *       }
     *      m1
     * }
     * map0[k0] = v0
     * ```
     */
    private fun renderMapEntry(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        isSparse: Boolean,
        parentMemberName: String,
    ) {
        val keyName = nestingLevel.variableNameFor(NestedIdentifierType.KEY)
        val valueName = nestingLevel.variableNameFor(NestedIdentifierType.VALUE)
        val populateNullValuePostfix = if (isSparse) "" else "; continue"
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val mutableCollectionType = mapShape.mutableCollectionType()
        val nextNestingLevel = nestingLevel + 1
        val memberName = nextNestingLevel.variableNameFor(NestedIdentifierType.MAP)
        val collectionReturnExpression = collectionReturnExpression(rootMemberShape, memberName)

        writer.write("val $keyName = key()")
        writer.withBlock("val $valueName =", "") {
            withBlock("if (nextHasValue()) {", "} else { deserializeNull()$populateNullValuePostfix }") {
                withBlock("deserializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.deserializeMap) {
                    write("val $memberName = $mutableCollectionType()")
                    withBlock("while (hasNextEntry()) {", "}") {
                        delegateMapDeserialization(rootMemberShape, mapShape, nextNestingLevel, memberName)
                    }
                    write(collectionReturnExpression)
                }
            }
        }
        writer.write("$parentMemberName[$keyName] = $valueName")
    }

    /**
     * Renders a map value of type list.  Example:
     *
     * ```
     * val k0 = key()
     * val v0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
     *      val m1 = mutableSetOf<Int>()
     *      while (hasNextElement()) {
     *          ...
     *      }
     *      m1
     * }
     * map0[k0] = v0
     * ```
     */
    private fun renderListEntry(
        rootMemberShape: MemberShape,
        collectionShape: CollectionShape,
        nestingLevel: Int,
        isSparse: Boolean,
        parentMemberName: String,
    ) {
        val keyName = nestingLevel.variableNameFor(NestedIdentifierType.KEY)
        val valueName = nestingLevel.variableNameFor(NestedIdentifierType.VALUE)
        val populateNullValuePostfix = if (isSparse) "" else "; continue"
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val mutableCollectionType = collectionShape.mutableCollectionType()
        val nextNestingLevel = nestingLevel + 1
        val memberName = nextNestingLevel.variableNameFor(NestedIdentifierType.COLLECTION)
        val collectionReturnExpression = collectionReturnExpression(rootMemberShape, memberName)

        writer.write("val $keyName = key()")
        writer.withBlock("val $valueName =", "") {
            withBlock("if (nextHasValue()) {", "} else { deserializeNull()$populateNullValuePostfix }") {
                withBlock("deserializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.deserializeList) {
                    write("val $memberName = $mutableCollectionType()")
                    withBlock("while (hasNextElement()) {", "}") {
                        delegateListDeserialization(rootMemberShape, collectionShape, nextNestingLevel, memberName)
                    }
                    write(collectionReturnExpression)
                }
            }
        }
        writer.write("$parentMemberName[$keyName] = $valueName")
    }

    /**
     * Example:
     * ```
     * val k0 = key()
     * val el0 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
     * map0[k0] = el0
     * ```
     */
    private fun renderEntry(elementShape: Shape, nestingLevel: Int, isSparse: Boolean, parentMemberName: String) {
        val deserializerFn = deserializerForShape(elementShape)
        val keyName = nestingLevel.variableNameFor(NestedIdentifierType.KEY)
        val valueName = nestingLevel.variableNameFor(NestedIdentifierType.VALUE)
        val populateNullValuePostfix = if (isSparse) "" else "; continue"

        writer.write("val $keyName = key()")
        writer.write("val $valueName = if (nextHasValue()) { $deserializerFn } else { deserializeNull()$populateNullValuePostfix }")
        writer.write("$parentMemberName[$keyName] = $valueName")
    }

    /**
     * Example:
     * ```
     * PAYLOAD_DESCRIPTOR.index -> builder.payload =
     *  deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
     *      val col0 = mutableListOf<Instant>()
     *      while (hasNextElement()) {
     *          ...
     *      }
     *      col0
     *  }
     */
    protected fun renderListMemberDeserializer(memberShape: MemberShape, targetShape: CollectionShape) {
        val nestingLevel = 0
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        val descriptorName = memberShape.descriptorName()
        val mutableCollectionType = targetShape.mutableCollectionType()
        val valueCollector = deserializationResultName("builder.$memberName")
        val mutableCollectionName = nestingLevel.variableNameFor(NestedIdentifierType.COLLECTION)
        val collectionReturnExpression = collectionReturnExpression(memberShape, mutableCollectionName)

        writer.write("$descriptorName.index -> $valueCollector = ")
            .indent()
            .withBlock("deserializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.deserializeList) {
                write("val $mutableCollectionName = $mutableCollectionType()")
                withBlock("while (hasNextElement()) {", "}") {
                    delegateListDeserialization(memberShape, targetShape, nestingLevel, mutableCollectionName)
                }
                write(collectionReturnExpression)
            }
            .dedent()
    }

    /**
     * Delegates to other functions based on the type of element.
     */
    private fun delegateListDeserialization(rootMemberShape: MemberShape, listShape: CollectionShape, nestingLevel: Int, parentMemberName: String) {
        val elementShape = ctx.model.expectShape(listShape.member.target)
        val isSparse = listShape.hasTrait<SparseTrait>()

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
            ShapeType.BIG_INTEGER,
            ShapeType.BLOB,
            ShapeType.DOCUMENT,
            ShapeType.TIMESTAMP,
            ShapeType.ENUM,
            -> renderElement(elementShape, nestingLevel, isSparse, parentMemberName)

            ShapeType.LIST,
            ShapeType.SET,
            -> renderListElement(rootMemberShape, elementShape as CollectionShape, nestingLevel, parentMemberName)

            ShapeType.MAP -> renderMapElement(rootMemberShape, elementShape as MapShape, nestingLevel, parentMemberName)
            ShapeType.UNION,
            ShapeType.STRUCTURE,
            -> renderNestedStructureElement(elementShape, nestingLevel, isSparse, parentMemberName)

            ShapeType.INT_ENUM -> error("IntEnum is not supported until Smithy 2.0")

            else -> error("Unhandled type ${elementShape.type}")
        }
    }

    /**
     * Example:
     * ```
     * val el0 = if (nextHasValue()) { NestedStructureDeserializer().deserialize(deserializer) } else { deserializeNull(); continue }
     * col0.add(el0)
     * ```
     */
    private fun renderNestedStructureElement(elementShape: Shape, nestingLevel: Int, isSparse: Boolean, parentMemberName: String) {
        val deserializer = deserializerForShape(elementShape)
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val populateNullValuePostfix = if (isSparse) "" else "; continue"
        if (elementShape.isStructureShape || elementShape.isUnionShape) {
            val symbol = ctx.symbolProvider.toSymbol(elementShape)
            writer.addImport(symbol)
        }

        writer.write("val $elementName = if (nextHasValue()) { $deserializer } else { deserializeNull()$populateNullValuePostfix }")
        writer.write("$parentMemberName.add($elementName)")
    }

    /**
     * Renders the deserialization of a list element of type map.
     *
     * Example:
     * ```
     * val el0 = deserializer.deserializeMap(PAYLOAD_C0_DESCRIPTOR) {
     *      val m1 = mutableMapOf<String, String>()
     *      while (hasNextEntry()) {
     *          ...
     *      }
     *      m1
     * }
     * col0.add(el0)
     * ```
     */
    private fun renderMapElement(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMapMemberName: String,
    ) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val nextNestingLevel = nestingLevel + 1
        val mapName = nextNestingLevel.variableNameFor(NestedIdentifierType.MAP)
        val mutableCollectionType = mapShape.mutableCollectionType()
        val collectionReturnExpression = collectionReturnExpression(rootMemberShape, mapName)

        writer.withBlock("val $elementName = deserializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.deserializeMap) {
            write("val $mapName = $mutableCollectionType()")
            withBlock("while (hasNextEntry()) {", "}") {
                delegateMapDeserialization(rootMemberShape, mapShape, nextNestingLevel, mapName)
            }
            write(collectionReturnExpression)
        }
        writer.write("$parentMapMemberName.add($elementName)")
    }

    /**
     * Render a List element of type List
     *
     * Example:
     *
     * ```
     * val el0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
     *      val m1 = mutableListOf<String>()
     *      while (hasNextElement()) {
     *          ...
     *      }
     *      m1
     * }
     * col0.add(el0)
     */
    private fun renderListElement(rootMemberShape: MemberShape, elementShape: CollectionShape, nestingLevel: Int, parentListMemberName: String) {
        val descriptorName = rootMemberShape.descriptorName(nestingLevel.nestedDescriptorName())
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val nextNestingLevel = nestingLevel + 1
        val listName = nextNestingLevel.variableNameFor(NestedIdentifierType.COLLECTION)
        val mutableCollectionType = elementShape.mutableCollectionType()
        val collectionReturnExpression = collectionReturnExpression(rootMemberShape, listName)

        writer.withBlock("val $elementName = deserializer.#T($descriptorName) {", "}", RuntimeTypes.Serde.deserializeList) {
            write("val $listName = $mutableCollectionType()")
            withBlock("while (hasNextElement()) {", "}") {
                delegateListDeserialization(rootMemberShape, elementShape, nextNestingLevel, listName)
            }
            write(collectionReturnExpression)
        }
        writer.write("$parentListMemberName.add($elementName)")
    }

    /**
     * Example:
     * ```
     * val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
     * col0.add(el0)
     * ```
     */
    private fun renderElement(elementShape: Shape, nestingLevel: Int, isSparse: Boolean, listMemberName: String) {
        val deserializerFn = deserializerForShape(elementShape)
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val populateNullValuePostfix = if (isSparse) "" else "; continue"

        writer.write("val $elementName = if (nextHasValue()) { $deserializerFn } else { deserializeNull()$populateNullValuePostfix }")
        writer.write("$listMemberName.add($elementName)")
    }

    /**
     * Return Kotlin function that deserializes a primitive value.
     * @param shape primitive [Shape] associated with value.
     */
    protected fun deserializerForShape(shape: Shape): String {
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
            target.type == ShapeType.DOCUMENT -> "deserializeDocument()"

            target.type == ShapeType.BLOB -> {
                writer.addImport("decodeBase64Bytes", KotlinDependency.UTILS)
                "deserializeString().decodeBase64Bytes()"
            }

            target.type == ShapeType.TIMESTAMP -> {
                writer.addImport(RuntimeTypes.Core.Instant)
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

            target.isEnum -> {
                val enumSymbol = ctx.symbolProvider.toSymbol(target)
                writer.addImport(enumSymbol)
                "deserializeString().let { ${enumSymbol.name}.fromValue(it) }"
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

    // Return the function to generate a mutable instance of collection type of input shape.
    private fun MapShape.mutableCollectionType(): String =
        ctx.symbolProvider.toSymbol(this).getProperty(SymbolProperty.MUTABLE_COLLECTION_FUNCTION).get() as String

    // Return the function to generate a mutable instance of collection type of input shape.
    private fun CollectionShape.mutableCollectionType(): String =
        ctx.symbolProvider.toSymbol(this).getProperty(SymbolProperty.MUTABLE_COLLECTION_FUNCTION).get() as String
}
