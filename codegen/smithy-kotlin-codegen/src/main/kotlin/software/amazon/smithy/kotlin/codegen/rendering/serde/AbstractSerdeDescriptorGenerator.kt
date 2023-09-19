/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.addImport
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape

/**
 * Codegen representation of client-runtime SdkFieldDescriptor trait
 *
 * @param symbol the runtime symbol of the trait
 * @param args a list of constructor arguments to pass when instantiating the symbol.
 *
 * NOTE: [args] should already be formatted/quoted as necessary for rendering
 */
data class SdkFieldDescriptorTrait(val symbol: Symbol, val args: List<String> = emptyList()) {

    constructor(symbol: Symbol, vararg args: String) : this(symbol, args.toList())

    override fun toString(): String {
        if (args.isEmpty()) return symbol.name
        val formattedArgs = args.joinToString(separator = ", ")
        return "${symbol.name}($formattedArgs)"
    }
}

// convenience functions for sub-classes creating their list of descriptors
fun MutableList<SdkFieldDescriptorTrait>.add(symbol: Symbol) = add(SdkFieldDescriptorTrait(symbol))
fun MutableList<SdkFieldDescriptorTrait>.add(symbol: Symbol, vararg params: String) = add(SdkFieldDescriptorTrait(symbol, params.toList()))

/**
 * Base class that most formats/protocols should extend for rendering serde descriptors
 *
 * @param ctx the rendering context to use to render the descriptors. [RenderingContext.shape] MUST be the
 * structure shape to render a serializer/deserializer for
 * @param memberShapes the members to generate field descriptors for. Defaults to all member shapes of the structure.
 * This may be overridden if for instance field descriptors should only be generated for a subset of member shapes
 * because some are bound to other locations (e.g. headers, query, etc)
 */
abstract class AbstractSerdeDescriptorGenerator(
    protected val ctx: RenderingContext<Shape>,
    memberShapes: List<MemberShape>? = null,
    private val isJsonProtocol: Boolean = false,
) : SerdeDescriptorGenerator {

    protected val objectShape = requireNotNull(ctx.shape) { "rendering context requires shape to not be null" }
    protected val memberShapes = memberShapes ?: objectShape.members()
    protected val writer = ctx.writer

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
         * This is here to then ignore it when deserializing unions for AWS restJson1, awsJson1_0, and awsJson1_1
         *
         * Source: https://github.com/smithy-lang/smithy/pull/1945
         * Also see: [JsonDeserializerTest]
         */
        if (objectShape.isUnionShape && isJsonProtocol && "__type" !in memberShapes.map { it.memberName }) writer.write("val __TYPE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName(\"__type\"))")

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
             * This is here to then ignore it when deserializing unions for AWS restJson1, awsJson1_0, and awsJson1_1
             *
             * Source: https://github.com/smithy-lang/smithy/pull/1945
             * Also see: [JsonDeserializerTest]
             */
            if (objectShape.isUnionShape && isJsonProtocol && "__type" !in memberShapes.map { it.memberName }) write("field(__TYPE_DESCRIPTOR)")
        }
        writer.write("")
    }

    /**
     * Render the traits for the object descriptor.
     * This function is invoked inside the `build` method of the `SdkObjectDescriptor`
     */
    protected open fun getObjectDescriptorTraits(): List<SdkFieldDescriptorTrait> = emptyList()

    /**
     * Return the list of formatted traits to apply when rendering the descriptor for [member].
     *
     * @param member The member shape a descriptor is being rendered for
     * @param targetShape The target shape type
     * This will normally be the same as [MemberShape.target]
     * @param nameSuffix An optional suffix to be applied to the name
     */
    protected open fun getFieldDescriptorTraits(
        member: MemberShape,
        targetShape: Shape,
        nameSuffix: String = "",
    ): List<SdkFieldDescriptorTrait> = emptyList()

    /**
     * Render an SdkFieldDescriptor for [member]
     *
     * @param member The member shape to render a descriptor for
     * @param targetShape The target shape type to render a descriptor for (SerialKind).
     * This will normally be the same as [MemberShape.target]
     * @param nameSuffix An optional suffix to be applied to the name
     */
    protected open fun renderFieldDescriptor(member: MemberShape, targetShape: Shape, nameSuffix: String = "") {
        val descriptorName = member.descriptorName(nameSuffix)
        val serialKind = targetShape.serialKind()

        val traits = getFieldDescriptorTraits(member, targetShape, nameSuffix)
        if (traits.isEmpty()) {
            writer.write("val #L = SdkFieldDescriptor(#L)", descriptorName, serialKind)
        } else {
            traits.forEach { trait -> writer.addImport(trait.symbol) }
            writer.write(
                "val #L = SdkFieldDescriptor(#L, #L)",
                descriptorName,
                serialKind,
                traits.joinToString(separator = ", "),
            )
        }
    }

    /**
     * Render field descriptors for a member's target shape that is itself a container
     *
     * e.g.
     * ```
     * structure Foo {
     *     bar: MapOfList
     * }
     *
     * map MapOfList {
     *     key: String,
     *     value: IntList
     * }
     *
     * list IntList {
     *     member: Integer
     * }
     * ```
     *
     * This model needs a field descriptor for both the (root) member `Foo.bar` as well as one that describes the list
     * type `MapOfList.value`
     *
     * ```
     * val BAR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, ...)
     * val BAR_C0_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, ...)
     * ```
     *
     * @param member The (root) member shape
     * @param targetShape The nested container shape type to generate a field descriptor for
     * @param level the current child nesting level (e.g. if we have mapOfMapOfList we need to generate descriptors recursively)
     */
    private fun renderContainerFieldDescriptors(member: MemberShape, targetShape: Shape, level: Int = 0) {
        renderFieldDescriptor(member, targetShape, "_C$level")

        // container of container (e.g. mapOfMap, listOfList, etc)
        val nestedTarget = targetShape.childShape(ctx.model)
        if (nestedTarget?.isContainerShape() == true) renderContainerFieldDescriptors(member, nestedTarget, level + 1)
    }
}
