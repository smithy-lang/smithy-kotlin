/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeUnionGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    private val unionName: String,
    members: List<MemberShape>,
    writer: KotlinWriter,
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
            // field iterators MUST be driven to completion so that underlying tokens are consumed
            // and the deserializer state is maintained
            withBlock("loop@while(true) {", "}") {
                withBlock("when(findNextFieldIndex()) {", "}") {
                    members
                        .sortedBy { it.memberName }
                        .forEach { memberShape -> renderMemberShape(memberShape) }
                    write("null -> break@loop")
                    write("else -> value = $unionName.SdkUnknown.also { skipValue() }")
                }
            }
        }
    }

    /**
     * Deserialize top-level members.
     */
    override fun renderMemberShape(memberShape: MemberShape) {
        when (val targetShape = ctx.model.expectShape(memberShape.target)) {
            is ListShape -> renderListMemberDeserializer(memberShape, targetShape as CollectionShape)
            is MapShape -> renderMapMemberDeserializer(memberShape, targetShape)
            is StructureShape,
            is UnionShape -> renderShapeDeserializer(memberShape)
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
            is BigIntegerShape -> renderShapeDeserializer(memberShape)
            else -> error("Unexpected shape type: ${targetShape.type}")
        }
    }

    /**
     * Generate the union deserializer for a primitive member. Example:
     * ```
     * I32_DESCRIPTOR.index -> value = deserializeInt().let { PrimitiveUnion.I32(it) }
     * ```
     */
    override fun renderShapeDeserializer(memberShape: MemberShape) {
        val unionTypeName = memberShape.unionTypeName(ctx)
        val descriptorName = memberShape.descriptorName()
        val deserialize = deserializerForShape(memberShape)

        writer.write("$descriptorName.index -> value = $unionTypeName($deserialize)")
    }

    // Union response types hold a single value for any variant
    override fun deserializationResultName(defaultName: String): String = "value"

    // Return the type that deserializes the incoming value.  Example: `MyAggregateUnion.IntList`
    override fun collectionReturnExpression(memberShape: MemberShape, defaultCollectionName: String): String {
        val unionTypeName = memberShape.unionTypeName(ctx)
        return "$unionTypeName($defaultCollectionName)"
    }
}
