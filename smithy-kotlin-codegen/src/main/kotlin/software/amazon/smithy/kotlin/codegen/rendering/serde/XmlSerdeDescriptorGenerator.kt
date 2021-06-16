/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.model.traits.SyntheticClone
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*

private typealias SerdeXml = RuntimeTypes.Serde.SerdeXml

/**
 * Field descriptor generator that processes the [XML Binding Traits](https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html#)
 */
open class XmlSerdeDescriptorGenerator(
    ctx: RenderingContext<Shape>,
    memberShapes: List<MemberShape>? = null
) : AbstractSerdeDescriptorGenerator(ctx, memberShapes) {

    private val serviceShape = ctx.model.expectShape<ServiceShape>(ctx.settings.service)

    override fun getObjectDescriptorTraits(): List<SdkFieldDescriptorTrait> {
        val objTraits = mutableListOf<SdkFieldDescriptorTrait>()
        val serialName = when {
            // FIXME - we should be able to remove special casing of errors here which is protocol specific
            // see https://github.com/awslabs/smithy-kotlin/issues/350
            objectShape.hasTrait<ErrorTrait>() -> "Error"
            objectShape.hasTrait<XmlNameTrait>() -> objectShape.expectTrait<XmlNameTrait>().value
            objectShape.hasTrait<SyntheticClone>() -> objectShape.expectTrait<SyntheticClone>().archetype.name
            else -> objectShape.defaultName(serviceShape)
        }

        objTraits.add(SerdeXml.XmlSerialName, serialName.dq())

        if (objectShape.hasTrait<ErrorTrait>()) {
            objTraits.add(SerdeXml.XmlError)
        }

        // namespace trait if present comes from the struct or falls back to the service
        val namespaceTrait: XmlNamespaceTrait? = objectShape.getTrait() ?: serviceShape.getTrait()
        if (namespaceTrait != null) {
            writer.addImport(SerdeXml.XmlNamespace)
            val serdeTrait = namespaceTrait.toSdkTrait()
            objTraits.add(serdeTrait)
        }

        return objTraits
    }

    /**
     * Gets the serial name for the given member, taking into account [XmlNameTrait] if present.
     */
    protected fun getSerialName(member: MemberShape, nameSuffix: String): String = when {
        nameSuffix.isEmpty() -> member.getTrait<XmlNameTrait>()?.value ?: member.memberName
        else -> member.getTrait<XmlNameTrait>()?.value ?: "member"
    }

    override fun getFieldDescriptorTraits(
        member: MemberShape,
        targetShape: Shape,
        nameSuffix: String
    ): List<SdkFieldDescriptorTrait> {

        ctx.writer.addImport(
            RuntimeTypes.Serde.SerdeXml.XmlDeserializer,
            RuntimeTypes.Serde.SerdeXml.XmlSerialName,
        )

        val traitList = mutableListOf<SdkFieldDescriptorTrait>()

        val serialName = getSerialName(member, nameSuffix)
        traitList.add(SerdeXml.XmlSerialName, serialName.dq())

        val memberTarget = ctx.model.expectShape(member.target)
        val isNestedMap = memberTarget.isMapShape && targetShape.isMapShape && nameSuffix.isNotEmpty()
        val flattened = member.getTrait<XmlFlattenedTrait>()?.also {
            if (!isNestedMap) {
                traitList.add(it.toSdkTrait())
            }
        } != null
        member.getTrait<XmlAttributeTrait>()?.let { traitList.add(it.toSdkTrait()) }
        member.getTrait<XmlNamespaceTrait>()?.let { traitList.add(it.toSdkTrait()) }

        when (targetShape.type) {
            ShapeType.LIST, ShapeType.SET -> {
                val collectionMember = (targetShape as CollectionShape).member
                if (!flattened && collectionMember.hasTrait<XmlNameTrait>()) {
                    // flattened collections should only need the XmlSerialName trait since there is no <member> element
                    val memberName = collectionMember.expectTrait<XmlNameTrait>().value
                    traitList.add(SerdeXml.XmlCollectionName, memberName.dq())
                }

                if (collectionMember.hasTrait<XmlNamespaceTrait>()) {
                    val ns = collectionMember.expectTrait<XmlNamespaceTrait>()
                    val nsTrait = ns.toSdkTrait(SerdeXml.XmlCollectionValueNamespace)
                    traitList.add(nsTrait)
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
                    traitList.add(SerdeXml.XmlMapName, *it.toTypedArray())
                    writer.addImport(SerdeXml.XmlMapName)
                }

                mapMember.key
                    .getTrait<XmlNamespaceTrait>()
                    ?.toSdkTrait(SerdeXml.XmlMapKeyNamespace)
                    ?.let {
                        traitList.add(it)
                    }

                mapMember.value
                    .getTrait<XmlNamespaceTrait>()
                    ?.toSdkTrait(SerdeXml.XmlCollectionValueNamespace)
                    ?.let {
                        traitList.add(it)
                    }
            }
        }

        return traitList
    }
}

private fun XmlNamespaceTrait.toSdkTrait(namespaceTraitSymbol: Symbol = SerdeXml.XmlNamespace): SdkFieldDescriptorTrait =
    if (prefix.isPresent) {
        SdkFieldDescriptorTrait(namespaceTraitSymbol, uri.dq(), prefix.get().dq())
    } else {
        SdkFieldDescriptorTrait(namespaceTraitSymbol, uri.dq())
    }

private fun XmlAttributeTrait.toSdkTrait(): SdkFieldDescriptorTrait = SdkFieldDescriptorTrait(SerdeXml.XmlAttribute)
private fun XmlFlattenedTrait.toSdkTrait(): SdkFieldDescriptorTrait = SdkFieldDescriptorTrait(SerdeXml.Flattened)
