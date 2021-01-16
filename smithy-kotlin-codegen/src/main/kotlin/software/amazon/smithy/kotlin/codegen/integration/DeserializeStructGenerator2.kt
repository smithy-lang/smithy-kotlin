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
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Generate deserialization for members bound to the payload.
 *
 * e.g.
 * ```
 * deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
 *    loop@while(true) {
 *        when(findNextFieldIndex()) {
 *             FIELD1_DESCRIPTOR.index -> builder.field1 = deserializeString()
 *             FIELD2_DESCRIPTOR.index -> builder.field2 = deserializeInt()
 *             null -> break@loop
 *             else -> skipValue()
 *         }
 *     }
 * }
 * ```
 */
class DeserializeStructGenerator2(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) {

    /**
     * Iterate over all supplied [MemberShape]s to generate serializers.
     */
    open fun render() {
        // inline an empty object descriptor when the struct has no members
        // otherwise use the one generated as part of the companion object
        val objDescriptor = if (members.isNotEmpty()) "OBJ_DESCRIPTOR" else "SdkObjectDescriptor.build {}"
        writer.withBlock("deserializer.deserializeStruct($objDescriptor) {", "}") {
            withBlock("loop@while(true) {", "}") {
                withBlock("when(findNextFieldIndex()) {", "}") {
                    members.sortedBy { it.memberName }.forEach { memberShape ->
                        renderMemberShape(memberShape)
                    }
                }
            }
        }
    }

    private fun renderMemberShape(memberShape: MemberShape) {
        val targetShape = ctx.model.expectShape(memberShape.target)

        when (targetShape.type) {
            ShapeType.LIST,
            ShapeType.SET -> renderListMemberDeserializer(memberShape, targetShape as CollectionShape)
            ShapeType.MAP -> renderMapMemberDeserializer(memberShape, targetShape as MapShape)
            ShapeType.STRUCTURE,
            ShapeType.UNION -> renderPrimitiveShapeDeserializer(memberShape, ::deserializerForStructureShape)
            ShapeType.DOCUMENT -> renderDocumentShapeDeserializer(memberShape)
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
            ShapeType.BIG_INTEGER -> renderPrimitiveShapeDeserializer(memberShape, ::deserializerForPrimitiveShape)
            else -> error("Unexpected shape type: ${targetShape.type}")
        }
    }

    private fun renderDocumentShapeDeserializer(memberShape: MemberShape) {
        TODO("Not yet implemented")
    }

    private fun renderPrimitiveShapeDeserializer(memberShape: MemberShape, any: Any) {
        // TODO("Not yet implemented")
    }

    private fun renderMapMemberDeserializer(memberShape: MemberShape, mapShape: MapShape) {
        // TODO("Not yet implemented")
    }

    private fun renderListMemberDeserializer(memberShape: MemberShape, collectionShape: CollectionShape) {
        // TODO("Not yet implemented")
    }

    private fun deserializerForStructureShape(shape: Shape): SerializeStructGenerator.SerializeInfo {
        TODO("nOT")
    }

    private fun deserializerForPrimitiveShape(shape: Shape): SerializeStructGenerator.SerializeInfo {
        TODO("nOT")
    }
}
