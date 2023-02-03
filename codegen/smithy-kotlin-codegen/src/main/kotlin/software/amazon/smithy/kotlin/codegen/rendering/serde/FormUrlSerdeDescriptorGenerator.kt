/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.traits.SyntheticClone
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.shapes.*

private typealias SerdeFormUrl = RuntimeTypes.Serde.SerdeFormUrl

open class FormUrlSerdeDescriptorGenerator(
    ctx: RenderingContext<Shape>,
    memberShapes: List<MemberShape>? = null,
) : AbstractSerdeDescriptorGenerator(ctx, memberShapes) {

    protected val service: ServiceShape by lazy { ctx.model.expectShape<ServiceShape>(ctx.settings.service) }

    /**
     * The serialized name for the main object shape of this generator.
     */
    open val objectSerialName: String
        get() = objectShape.getTrait<SyntheticClone>()?.archetype?.name ?: objectShape.defaultName(service)

    override fun getObjectDescriptorTraits(): List<SdkFieldDescriptorTrait> {
        val traits = mutableListOf<SdkFieldDescriptorTrait>()
        traits.add(SerdeFormUrl.FormUrlSerialName, objectSerialName.dq())
        return traits
    }

    final override fun getFieldDescriptorTraits(
        member: MemberShape,
        targetShape: Shape,
        nameSuffix: String,
    ): List<SdkFieldDescriptorTrait> {
        val traits = mutableListOf<SdkFieldDescriptorTrait>()
        if (nameSuffix.isNotEmpty()) return traits

        val serialName = getMemberSerialName(member)
        traits.add(SerdeFormUrl.FormUrlSerialName, serialName.dq())

        val flattened = isMemberFlattened(member, targetShape)
        if (flattened) {
            traits.add(SerdeFormUrl.Flattened)
        }

        when (targetShape.type) {
            ShapeType.LIST, ShapeType.SET -> {
                val collectionMember = (targetShape as CollectionShape).member
                val memberName = getMemberSerialName(collectionMember)
                if (!flattened && memberName != collectionMember.memberName) {
                    // flattened collections should only need the name trait since there is no <member> element
                    traits.add(SerdeFormUrl.FormUrlCollectionName, memberName.dq())
                }
            }
            ShapeType.MAP -> {
                val mapMember = targetShape as MapShape

                val customKeyName = getMemberSerialNameOverride(mapMember.key)
                val customValueName = getMemberSerialNameOverride(mapMember.value)

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
            else -> { } // No action needed
        }

        return traits
    }

    private fun getMemberSerialName(member: MemberShape): String =
        getMemberSerialNameOverride(member) ?: member.memberName

    /**
     * Gets any applicable name override for a [MemberShape]. Implementing protocols can check specific traits or other
     * conditions to determine whether an override is in effect. By default, there is no override.
     */
    open fun getMemberSerialNameOverride(member: MemberShape): String? = null

    /**
     * Determines whether a [MemberShape] should be flattened. Implementing protocols can check specific traits or other
     * conditions to determinw whether flattening should occur. By default, members are not flattened.
     */
    open fun isMemberFlattened(member: MemberShape, targetShape: Shape): Boolean = false
}
