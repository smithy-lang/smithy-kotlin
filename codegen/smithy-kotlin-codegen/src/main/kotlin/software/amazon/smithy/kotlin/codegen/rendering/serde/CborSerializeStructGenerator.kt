/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.wrapBlockIf
import software.amazon.smithy.kotlin.codegen.model.isEnum
import software.amazon.smithy.kotlin.codegen.model.isSparse
import software.amazon.smithy.kotlin.codegen.model.targetOrSelf
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * An implementation of [SerializeStructGenerator] with special-cased blob serialization.
 */
open class CborSerializeStructGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    members: List<MemberShape>,
    writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format,
) : SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat) {

    override fun delegateMapSerialization(
        rootMemberShape: MemberShape,
        mapShape: MapShape,
        nestingLevel: Int,
        parentMemberName: String,
    ) {
        val keyShape = ctx.model.expectShape(mapShape.key.target)
        val elementShape = ctx.model.expectShape(mapShape.value.target)
        val isSparse = mapShape.isSparse

        when (elementShape.type) {
            ShapeType.BLOB -> renderBlobEntry(keyShape, nestingLevel, parentMemberName, isSparse)
            else -> super.delegateMapSerialization(rootMemberShape, mapShape, nestingLevel, parentMemberName)
        }
    }

    /**
     * Renders the serialization of a blob value contained by a map.  Example:
     *
     * ```
     * input.fooBlobMap.forEach { (key, value) -> entry(key, value.encodeBase64String()) }
     * ```
     */
    private fun renderBlobEntry(keyShape: Shape, nestingLevel: Int, listMemberName: String, isSparse: Boolean) {
        val containerName = if (nestingLevel == 0) "input." else ""
        val (keyName, valueName) = keyValueNames(nestingLevel)
        val keyValue = keyValue(keyShape, keyName)

        writer.withBlock("$containerName$listMemberName.forEach { ($keyName, $valueName) ->", "}") {
            writer.wrapBlockIf(isSparse, "if ($valueName != null) {", "} else entry($keyValue, null as String?)") {
                writer.write("entry($keyValue, $valueName)")
            }
        }
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

    override fun delegateListSerialization(
        rootMemberShape: MemberShape,
        listShape: CollectionShape,
        nestingLevel: Int,
        parentMemberName: String,
    ) {
        val elementShape = ctx.model.expectShape(listShape.member.target)
        val isSparse = listShape.isSparse

        when (elementShape.type) {
            ShapeType.BLOB -> renderBlobElement(nestingLevel, parentMemberName, isSparse)
            else -> super.delegateListSerialization(rootMemberShape, listShape, nestingLevel, parentMemberName)
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
    private fun renderBlobElement(nestingLevel: Int, listMemberName: String, isSparse: Boolean) {
        val elementName = nestingLevel.variableNameFor(NestedIdentifierType.ELEMENT)
        val containerName = if (nestingLevel == 0) "input." else ""

        writer.withBlock("for ($elementName in $containerName$listMemberName) {", "}") {
            writer.wrapBlockIf(isSparse, "if ($elementName != null) {", "} else serializeNull()") {
                writer.write("serializeBlob($elementName)")
            }
        }
    }

    override val serializerForSimpleShape = SerializeFunction { member, identifier ->
        // target shape type to deserialize is either the shape itself or member.target
        val target = member.targetOrSelf(ctx.model)

        val encoded = when {
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
}
