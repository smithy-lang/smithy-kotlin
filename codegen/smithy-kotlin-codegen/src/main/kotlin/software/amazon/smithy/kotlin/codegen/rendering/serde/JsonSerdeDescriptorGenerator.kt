/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.addImport
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.JsonNameTrait

/**
 * Field descriptor generator that processes the [jsonName trait](https://awslabs.github.io/smithy/1.0/spec/core/protocol-traits.html#jsonname-trait)
 * @param supportsJsonNameTrait Flag indicating if the jsonName trait should be used or not, when `false` the member name is used.
 */
open class JsonSerdeDescriptorGenerator(
    ctx: RenderingContext<Shape>,
    memberShapes: List<MemberShape>? = null,
    private val supportsJsonNameTrait: Boolean = true,
) : AbstractSerdeDescriptorGenerator(ctx, memberShapes) {

    override fun render() {
        if (memberShapes.isEmpty()) return

        // FIXME - decompose these symbols directly when they are emitted
        val serdeDescriptorSymbols = setOf(
            RuntimeTypes.Serde.SdkFieldDescriptor,
            RuntimeTypes.Serde.SdkObjectDescriptor,
            RuntimeTypes.Serde.SerialKind,
            RuntimeTypes.Serde.deserializeStruct,
            RuntimeTypes.Serde.deserializeList,
            RuntimeTypes.Serde.deserializeMap,
            RuntimeTypes.Serde.field,
            RuntimeTypes.Serde.asSdkSerializable,
            RuntimeTypes.Serde.serializeStruct,
            RuntimeTypes.Serde.serializeList,
            RuntimeTypes.Serde.serializeMap,
        )
        writer.addImport(serdeDescriptorSymbols)
        val sortedMembers = memberShapes.sortedBy { it.memberName }
        for (member in sortedMembers) {
            val memberTarget = ctx.model.expectShape(member.target)
            renderFieldDescriptor(member, memberTarget)

            val nestedMember = memberTarget.childShape(ctx.model)
            if (nestedMember?.isContainerShape() == true) {
                renderContainerFieldDescriptors(member, nestedMember)
            }
        }

        /**
         * Older implementations of AWS JSON protocols will unnecessarily serialize a '__type' property.
         * This property should be ignored for unions unless there is an explicit '__type' member in the model for:
         * AWS restJson1, awsJson1_0, and awsJson1_1
         *
         * Source: https://github.com/smithy-lang/smithy/pull/1945
         * Also see: [JsonDeserializerTest]
         */
        if (objectShape.isUnionShape && "__type" !in memberShapes.map { it.memberName }) {
            writer.write("val __TYPE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName(\"__type\"))")
        }

        writer.withBlock("val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {", "}") {
            val objTraits = getObjectDescriptorTraits()
            objTraits.forEach { trait ->
                writer.addImport(trait.symbol)
                writer.write("trait($trait)")
            }

            for (member in sortedMembers) {
                write("field(#L)", member.descriptorName())
            }

            /**
             * Older implementations of AWS JSON protocols will unnecessarily serialize a '__type' property.
             * This property should be ignored for unions unless there is an explicit '__type' member in the model for:
             * AWS restJson1, awsJson1_0, and awsJson1_1
             *
             * Source: https://github.com/smithy-lang/smithy/pull/1945
             * Also see: [JsonDeserializerTest]
             */
            if (objectShape.isUnionShape && "__type" !in memberShapes.map { it.memberName }) {
                write("field(__TYPE_DESCRIPTOR)")
            }
        }
        writer.write("")
    }

    override fun getFieldDescriptorTraits(
        member: MemberShape,
        targetShape: Shape,
        nameSuffix: String,
    ): List<SdkFieldDescriptorTrait> {
        if (nameSuffix.isNotBlank()) return emptyList()

        ctx.writer.addImport(
            RuntimeTypes.Serde.SerdeJson.JsonDeserializer,
            RuntimeTypes.Serde.SerdeJson.JsonSerialName,
        )

        val serialName = if (supportsJsonNameTrait) {
            member.getTrait<JsonNameTrait>()?.value ?: member.memberName
        } else {
            member.memberName
        }
        return listOf(SdkFieldDescriptorTrait(RuntimeTypes.Serde.SerdeJson.JsonSerialName, serialName.dq()))
    }
}
