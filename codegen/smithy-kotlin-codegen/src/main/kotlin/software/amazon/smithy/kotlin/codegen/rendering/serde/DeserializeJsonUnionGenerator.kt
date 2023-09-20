/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeJsonUnionGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    private val unionName: String,
    members: List<MemberShape>,
    writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format,
) : DeserializeUnionGenerator(ctx, unionName, members, writer, defaultTimestampFormat) {

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

                    /**
                     * Older implementations of AWS JSON protocols will unnecessarily serialize a '__type' property.
                     * This property should be ignored unless there is an explicit '__type' member in the model
                     *
                     * Source: https://github.com/smithy-lang/smithy/pull/1945
                     */
                    if ("__type" !in members.map { it.memberName }) write("__TYPE_DESCRIPTOR.index -> skipValue()")

                    write("null -> break@loop")
                    write("else -> value = $unionName.SdkUnknown.also { skipValue() }")
                }
            }
        }
    }
}
