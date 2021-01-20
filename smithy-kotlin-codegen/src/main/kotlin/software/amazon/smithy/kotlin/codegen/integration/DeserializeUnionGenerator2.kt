/*
 *
 *  * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  * SPDX-License-Identifier: Apache-2.0.
 *
 */

package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.defaultName
import software.amazon.smithy.kotlin.codegen.withBlock
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.utils.StringUtils

class DeserializeUnionGenerator2(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) : DeserializeStructGenerator2(ctx, members, writer, defaultTimestampFormat) {

    /**
     * Iterate over all supplied [MemberShape]s to generate serializers.
     */
    override fun render() {
        // inline an empty object descriptor when the struct has no members
        // otherwise use the one generated as part of the companion object
        val objDescriptor = if (members.isNotEmpty()) "OBJ_DESCRIPTOR" else "SdkObjectDescriptor.build {}"
        writer.withBlock("deserializer.deserializeStruct($objDescriptor) {", "}") {
            withBlock("when(findNextFieldIndex()) {", "}") {
                members
                    .sortedBy { it.memberName }
                    .map { ctx.model.expectShape(it.target) }
                    .forEach { memberShape ->
                        check(memberShape is UnionShape) { "Expected UnionShape but got ${memberShape.type}"}
                        renderUnionDeserializer(memberShape)
                    }
                write("else -> skipValue()")
            }
        }
    }

    private fun renderUnionDeserializer(memberShape: UnionShape) {
        memberShape.members().sortedBy { it.memberName }.forEach { unionMemberShape ->
            renderMemberShape(unionMemberShape)
        }
    }

    override fun renderPrimitiveShapeDeserializer(memberShape: MemberShape, any: Any) {
        val unionDeserializerExpression = memberShape.unionTypeName(memberShape)
        val descriptorName = memberShape.descriptorName()
        val deserialize = deserializerForPrimitiveShape(memberShape)

        writer.write("$descriptorName.index -> value = $deserialize.let { $unionDeserializerExpression(it) }")
    }

    override fun valueCollectorName(default: String): String = "value"

    override fun collectionReturnExpression(memberShape: MemberShape, defaultCollectionName: String): String {
        val unionTypeName = memberShape.unionTypeName(memberShape)
        return "$unionTypeName($defaultCollectionName)"
    }

    /**
     * Generate the fully qualified type name of Union variant
     */
    internal fun Shape.unionTypeName(unionVariant: MemberShape): String = "${this.id.name}.${StringUtils.capitalize(unionVariant.memberName)}"
}