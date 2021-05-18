/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.traits.OperationInput
import software.amazon.smithy.kotlin.codegen.model.traits.SyntheticClone
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.XmlFlattenedTrait
import software.amazon.smithy.model.traits.XmlNameTrait

private typealias SerdeFormUrl = RuntimeTypes.Serde.SerdeFormUrl

class FormUrlSerdeDescriptorGenerator(
    ctx: RenderingContext<Shape>,
    memberShapes: List<MemberShape>? = null
) : AbstractSerdeDescriptorGenerator(ctx, memberShapes) {

    override fun getObjectDescriptorTraits(): List<SdkFieldDescriptorTrait> {

        val traits = mutableListOf<SdkFieldDescriptorTrait>()
        val service = ctx.model.expectShape<ServiceShape>(ctx.settings.service)

        val serialName = when {
            objectShape.hasTrait<XmlNameTrait>() -> objectShape.expectTrait<XmlNameTrait>().value
            objectShape.hasTrait<SyntheticClone>() -> objectShape.expectTrait<SyntheticClone>().archetype.name
            else -> objectShape.defaultName(service)
        }
        traits.add(SerdeFormUrl.FormUrlSerialName, serialName.dq())

        val objectShape = requireNotNull(ctx.shape)
        if (objectShape.hasTrait<OperationInput>()) {
            // see https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#request-serialization

            // operation inputs are normalized in smithy-kotlin::OperationNormalizer to be "[OperationName]Request"
            val action = objectShape.id.name.removeSuffix("Request")
            val version = service.version
            traits.add(SerdeFormUrl.QueryLiteral, "Action".dq(), action.dq())
            traits.add(SerdeFormUrl.QueryLiteral, "Version".dq(), version.dq())
        }

        return traits
    }

    override fun getFieldDescriptorTraits(
        member: MemberShape,
        targetShape: Shape,
        nameSuffix: String
    ): List<SdkFieldDescriptorTrait> {
        val traits = mutableListOf<SdkFieldDescriptorTrait>()
        if (nameSuffix.isNotEmpty()) return traits

        val serialName = member.getTrait<XmlNameTrait>()?.value ?: member.memberName
        traits.add(SerdeFormUrl.FormUrlSerialName, serialName.dq())

        val flattened = member.getTrait<XmlFlattenedTrait>()?.also {
            traits.add(SerdeFormUrl.Flattened)
        } != null

        when (targetShape.type) {
            ShapeType.LIST, ShapeType.SET -> {
                val collectionMember = (targetShape as CollectionShape).member
                if (!flattened && collectionMember.hasTrait<XmlNameTrait>()) {
                    // flattened collections should only need the XmlSerialName trait since there is no <member> element
                    val memberName = collectionMember.expectTrait<XmlNameTrait>().value
                    traits.add(SerdeFormUrl.FormUrlCollectionName, memberName.dq())
                }
            }
            ShapeType.MAP -> {
                val mapMember = targetShape as MapShape

                val customKeyName = mapMember.key.getTrait<XmlNameTrait>()?.value
                val customValueName = mapMember.value.getTrait<XmlNameTrait>()?.value

                val mapTraitArgs = when {
                    customKeyName != null && customValueName != null -> listOf("key = ${customKeyName.dq()}", "value = ${customValueName.dq()}")
                    customKeyName != null -> listOf("key = ${customKeyName.dq()}")
                    customValueName != null -> listOf("value = ${customValueName.dq()}")
                    else -> null
                }

                mapTraitArgs?.let {
                    traits.add(SerdeFormUrl.FormUrlMapName, *it.toTypedArray())
                }
            }
        }

        return traits
    }
}
