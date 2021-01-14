package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.withBlock
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.utils.StringUtils

/**
 * Generate serialization for members of unions to the payload.
 *
 * Refer to [SerializeStructGenerator] for more details.
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
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format
): SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat) {

    // Unions do not directly nest, so parent is static.
    override fun parentName(defaultName: String): String = "value"

    // Return the union instance
    override fun valueToSerializeName(defaultName: String): String = "input.value"

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
                members.sortedBy { it.memberName }.forEach { memberShape ->
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
        val unionMemberName = memberShape.unionTypeName()
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
        val unionMemberName = memberShape.unionTypeName()
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
        val unionTypeName = memberShape.unionTypeName()
        val descriptorName = memberShape.descriptorName()

        writer.write("is $unionTypeName -> $serializeFn($descriptorName, $encoded)")
    }

    private fun MemberShape.unionTypeName(): String = "${id.name}.${StringUtils.capitalize(memberName)}"
}