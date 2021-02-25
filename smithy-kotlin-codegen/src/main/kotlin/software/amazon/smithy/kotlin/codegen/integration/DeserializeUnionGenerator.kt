/*
 *
 *  * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  * SPDX-License-Identifier: Apache-2.0.
 *
 */

package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.withBlock
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.utils.StringUtils

class DeserializeUnionGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val unionName: String,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format
) : DeserializeStructGenerator(ctx, members, writer, defaultTimestampFormat) {

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
                    .forEach { memberShape -> renderMemberShape(memberShape) }
                write("else -> value = $unionName.SdkUnknown.also { skipValue() }")
            }
        }
    }

    /**
     * Deserialize top-level members.
     */
    override fun renderMemberShape(memberShape: MemberShape) {
        when (val targetShape = ctx.model.expectShape(memberShape.target)) {
            is ListShape,
            is SetShape -> renderListMemberDeserializer(memberShape, targetShape as CollectionShape)
            is MapShape -> renderMapMemberDeserializer(memberShape, targetShape)
            is StructureShape -> renderPrimitiveShapeDeserializer(memberShape)
            is UnionShape -> targetShape.members().sortedBy { it.memberName }.forEach { renderMemberShape(it) }
            is DocumentShape -> renderDocumentShapeDeserializer(memberShape)
            is BlobShape,
            is BooleanShape,
            is StringShape,
            is TimestampShape,
            is ByteShape,
            is ShortShape,
            is IntegerShape,
            is LongShape,
            is FloatShape,
            is DoubleShape,
            is BigDecimalShape,
            is BigIntegerShape -> renderPrimitiveShapeDeserializer(memberShape)
            else -> error("Unexpected shape type: ${targetShape.type}")
        }
    }

    /**
     * Generate the union deserializer for a primitive member. Example:
     * ```
     * I32_DESCRIPTOR.index -> value = deserializeInt().let { PrimitiveUnion.I32(it) }
     * ```
     */
    override fun renderPrimitiveShapeDeserializer(memberShape: MemberShape) {
        val unionTypeName = memberShape.unionTypeName(memberShape)
        val descriptorName = memberShape.descriptorName()
        val deserialize = deserializerForShape(memberShape)

        writer.write("$descriptorName.index -> value = $unionTypeName($deserialize)")
    }

    // Union response types hold a single value for any variant
    override fun deserializationResultName(defaultName: String): String = "value"

    // Return the type that deserializes the incoming value.  Example: `MyAggregateUnion.IntList`
    override fun collectionReturnExpression(memberShape: MemberShape, defaultCollectionName: String): String {
        val unionTypeName = memberShape.unionTypeName(memberShape)
        return "$unionTypeName($defaultCollectionName)"
    }

    /**
     * Generate the fully qualified type name of Union variant
     */
    private fun Shape.unionTypeName(unionVariant: MemberShape): String = "${this.id.name}.${StringUtils.capitalize(unionVariant.memberName)}"
}
