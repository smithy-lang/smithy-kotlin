/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * An exact copy of [SerializeUnionGenerator], but inheriting from [CborSerializeStructGenerator] instead.
 */
class CborSerializeUnionGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    private val shape: UnionShape,
    members: List<MemberShape>,
    writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format,
) : CborSerializeStructGenerator(ctx, members, writer, defaultTimestampFormat) {
    // Unions do not directly nest, so parent is static.
    override fun parentName(defaultName: String): String = "value"

    // Return the union instance
    override fun valueToSerializeName(defaultName: String): String = when (defaultName) {
        "it" -> "input.value" // Union populates a singular value
        else -> defaultName // Otherwise return the default
    }

    /**
     * Iterate over all supplied [MemberShape]s to generate serializers. Example:
     *
     * ```
     * serializer.serializeStruct(OBJ_DESCRIPTOR) {
     *    when (input) {
     *      ...
     *    }
     *  }
     * ```
     */
    override fun render() {
        val unionSymbol = ctx.symbolProvider.toSymbol(shape)
        val objDescriptor = if (members.isNotEmpty()) "OBJ_DESCRIPTOR" else "SdkObjectDescriptor.build{}"
        writer.withBlock("serializer.serializeStruct($objDescriptor) {", "}") {
            writer.withBlock("when (input) {", "}") {
                members.forEach { memberShape ->
                    renderMemberShape(memberShape)
                }

                write(
                    """is #T.SdkUnknown -> throw #T("Cannot serialize SdkUnknown")""",
                    unionSymbol,
                    RuntimeTypes.Serde.SerializationException,
                )
            }
        }
    }

    /**
     * Produces serialization for a map member.  Example:
     * ```
     * is FooUnion.StrMapVal -> {
     *      mapField(STRMAPVAL_DESCRIPTOR) {
     *          ...
     *      }
     * }
     * ```
     */
    override fun renderMapMemberSerializer(memberShape: MemberShape, targetShape: MapShape) {
        val unionMemberName = memberShape.unionTypeName(ctx)
        val descriptorName = memberShape.descriptorName()
        val nestingLevel = 0

        writer.withBlock("is $unionMemberName -> {", "}") {
            writer.withBlock("mapField($descriptorName) {", "}") {
                delegateMapSerialization(memberShape, targetShape, nestingLevel, "value")
            }
        }
    }

    /**
     * Produces serialization for a list member.  Example:
     * ```
     * is FooUnion.IntListVal -> {
     *      listField(INTLISTVAL_DESCRIPTOR) {
     *          ...
     *      }
     * }
     * ```
     */
    override fun renderListMemberSerializer(memberShape: MemberShape, targetShape: CollectionShape) {
        val unionMemberName = memberShape.unionTypeName(ctx)
        val descriptorName = memberShape.descriptorName()
        val nestingLevel = 0

        writer.withBlock("is $unionMemberName -> {", "}") {
            writer.withBlock("listField($descriptorName) {", "}") {
                delegateListSerialization(memberShape, targetShape, nestingLevel, "value")
            }
        }
    }

    /**
     * Render code to serialize a primitive or structure shape. Example:
     *
     * ```
     * is FooUnion.StringMember -> field(TIMESTAMP4_DESCRIPTOR, input.value)
     * ```
     *
     * @param memberShape [MemberShape] referencing the primitive type
     */
    override fun renderShapeSerializer(
        memberShape: MemberShape,
        serializerFn: SerializeFunction,
    ) {
        val unionTypeName = memberShape.unionTypeName(ctx)
        val fn = serializerFn.format(memberShape, "input.value")
        writer.write("is $unionTypeName -> $fn")
    }

}