/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.unionVariantName
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Generate serialization for members of unions to the payload.
 *
 * Refer to [SerializeStructGenerator] for more details.
 *
 * NOTE: If the serialization order is important then [members] MUST already be sorted correctly
 *
 * Example output this class generates:
 * ```
 * serializer.serializeStruct(OBJ_DESCRIPTOR) {
 *     when (input) {
 *         is FooUnion.IntVal -> field(INTVAL_DESCRIPTOR, input.value)
 *         is FooUnion.StrVal -> field(STRVAL_DESCRIPTOR, input.value)
 *         is FooUnion.Timestamp4 -> field(TIMESTAMP4_DESCRIPTOR, input.value.format(TimestampFormat.ISO_8601))```
 *     }
 * }
 * ```
 */
class SerializeUnionGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    members: List<MemberShape>,
    writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format
) : SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat) {

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
        val objDescriptor = if (members.isNotEmpty()) "OBJ_DESCRIPTOR" else "SdkObjectDescriptor.build{}"
        writer.withBlock("serializer.serializeStruct($objDescriptor) {", "}") {
            writer.withBlock("when (input) {", "}") {
                members.forEach { memberShape ->
                    renderMemberShape(memberShape)
                }
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
     * is FooUnion.Timestamp4 -> field(TIMESTAMP4_DESCRIPTOR, input.value.format(TimestampFormat.ISO_8601))
     * ```
     *
     * @param memberShape [MemberShape] referencing the primitive type
     */
    override fun renderPrimitiveShapeSerializer(
        memberShape: MemberShape,
        serializerNameFn: (MemberShape) -> SerializeInfo
    ) {
        val (serializeFn, encoded) = serializerNameFn(memberShape)
        // FIXME - this doesn't account for unboxed primitives
        val unionTypeName = memberShape.unionTypeName(ctx)
        val descriptorName = memberShape.descriptorName()

        writer.write("is $unionTypeName -> $serializeFn($descriptorName, $encoded)")
    }
}

/**
 * Generate the fully qualified type name of Union variant
 * e.g. `FooUnion.VariantName`
 */
internal fun MemberShape.unionTypeName(ctx: ProtocolGenerator.GenerationContext): String {
    val unionShape = ctx.model.expectShape(id.withoutMember())
    val unionSymbol = ctx.symbolProvider.toSymbol(unionShape)
    val variantName = unionVariantName(ctx.symbolProvider)
    return "${unionSymbol.name}.$variantName"
}
