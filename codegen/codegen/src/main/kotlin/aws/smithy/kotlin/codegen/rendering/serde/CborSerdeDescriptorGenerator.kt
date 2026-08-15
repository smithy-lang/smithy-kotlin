/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.RenderingContext
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.utils.dq
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

    // The descriptor block is hoisted to file/top-level scope (see CborSerializerGenerator/CborParserGenerator) so
    // it is constructed once per class-load and reused across all (de)serialization calls. Rendering the descriptors
    // as file-private top-level properties keeps them from colliding with identically named descriptors in other
    // serde files that share the same package.
    override val descriptorModifier: String = "private "

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
