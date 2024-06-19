/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.shapes.*

/**
 * Field descriptor generator for CBOR.
 * Adds the object's serial name as a value of the `CborSerialName` field trait to be used for serialization.
 */
class CborSerdeDescriptorGenerator(
    ctx: RenderingContext<Shape>,
    memberShapes: List<MemberShape>? = null,
) : AbstractSerdeDescriptorGenerator(ctx, memberShapes) {

    private val serviceShape = ctx.model.expectShape<ServiceShape>(ctx.settings.service)

    override fun getObjectDescriptorTraits(): List<SdkFieldDescriptorTrait> {
        val objTraits = mutableListOf<SdkFieldDescriptorTrait>()
        val serialName = objectShape.defaultName(serviceShape)

        objTraits.add(RuntimeTypes.Serde.SerdeCbor.CborSerialName, serialName.dq())

        return objTraits
    }

    override fun getFieldDescriptorTraits(
        member: MemberShape,
        targetShape: Shape,
        nameSuffix: String,
    ): List<SdkFieldDescriptorTrait> {
        ctx.writer.addImport(RuntimeTypes.Serde.SerdeCbor.CborSerialName)

        val traitList = mutableListOf<SdkFieldDescriptorTrait>()
        traitList.add(RuntimeTypes.Serde.SerdeCbor.CborSerialName, (member.memberName + nameSuffix).dq())

        return traitList
    }
}
