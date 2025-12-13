/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols

import software.amazon.smithy.aws.traits.protocols.Ec2QueryNameTrait
import software.amazon.smithy.aws.traits.protocols.Ec2QueryTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.isNullable
import software.amazon.smithy.kotlin.codegen.protocols.core.AbstractQueryFormUrlSerializerGenerator
import software.amazon.smithy.kotlin.codegen.protocols.core.QueryHttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.protocols.formurl.QuerySerdeFormUrlDescriptorGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.toRenderingContext
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.XmlNameTrait

/**
 * Handles generating the aws.protocols#ec2Query protocol for services.
 */
class Ec2Query : QueryHttpBindingProtocolGenerator() {
    override val protocol: ShapeId = Ec2QueryTrait.ID

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        Ec2QuerySerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        Ec2QueryParserGenerator(this)

    override fun renderDeserializeErrorDetails(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        writer.write("""checkNotNull(payload){ "unable to parse error from empty response" }""")
        writer.write("#T(payload)", RuntimeTypes.AwsXmlProtocols.parseEc2QueryErrorResponse)
    }
}

private class Ec2QuerySerdeFormUrlDescriptorGenerator(
    ctx: RenderingContext<Shape>,
    memberShapes: List<MemberShape>? = null,
) : QuerySerdeFormUrlDescriptorGenerator(ctx, memberShapes) {
    /**
     * The serialized name for a shape. See
     * [EC2 query protocol](https://awslabs.github.io/smithy/1.0/spec/aws/aws-ec2-query-protocol.html#query-key-resolution)
     * for more information.
     */
    override val objectSerialName: String
        get() =
            objectShape.getTrait<Ec2QueryNameTrait>()?.value
                ?: objectShape.getTrait<XmlNameTrait>()?.value?.replaceFirstChar(Char::uppercaseChar)
                ?: super.objectSerialName

    override fun getMemberSerialNameOverride(member: MemberShape): String? =
        member.getTrait<Ec2QueryNameTrait>()?.value
            ?: member.getTrait<XmlNameTrait>()?.value?.replaceFirstChar(Char::uppercaseChar)
            ?: if (member.memberName.firstOrNull()?.isUpperCase() == false) {
                member.memberName.replaceFirstChar(Char::uppercaseChar)
            } else {
                null
            }

    override fun isMemberFlattened(member: MemberShape, targetShape: Shape): Boolean =
        targetShape.type == ShapeType.LIST
}

private class Ec2QuerySerializerGenerator(
    private val protocolGenerator: Ec2Query,
) : AbstractQueryFormUrlSerializerGenerator(protocolGenerator, protocolGenerator.defaultTimestampFormat) {

    override fun renderSerializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        // render the serde descriptors
        descriptorGenerator(ctx, shape, members, writer).render()
        when (shape) {
            is UnionShape -> SerializeUnionGenerator(ctx, shape, members, writer, protocolGenerator.defaultTimestampFormat).render()
            else -> Ec2QuerySerializeStructGenerator(ctx, members, writer, protocolGenerator.defaultTimestampFormat).render()
        }
    }

    override fun descriptorGenerator(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ): FormUrlSerdeDescriptorGenerator = Ec2QuerySerdeFormUrlDescriptorGenerator(ctx.toRenderingContext(protocolGenerator, shape, writer), members)
}

private class Ec2QueryParserGenerator(
    protocolGenerator: Ec2Query,
) : XmlParserGenerator(protocolGenerator.defaultTimestampFormat) {
    override fun unwrapOperationError(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        errorShape: StructureShape,
        writer: KotlinWriter,
    ): SerdeCtx {
        val unwrapFn = unwrapErrorResponse(ctx)
        writer.write("val errReader = #T(${serdeCtx.tagReader})", unwrapFn)
        return SerdeCtx("errReader")
    }

    /**
     * Error deserializer for a wrapped error response
     *
     * ```
     * <Response>
     *     <Errors>
     *         <Error>
     *             <-- DATA -->>
     *         </Error>
     *     </Errors>
     * </Response>
     * ```
     *
     * See https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html#operation-error-serialization
     */
    private fun unwrapErrorResponse(ctx: ProtocolGenerator.GenerationContext): Symbol = buildSymbol {
        name = "unwrapXmlErrorResponse"
        namespace = ctx.settings.pkg.serde
        definitionFile = "XmlErrorUtils.kt"
        renderBy = { writer ->
            writer.dokka("Handle [wrapped](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html#operation-error-serialization) error responses")
            writer.withBlock(
                "internal fun $name(root: #1T): #1T {",
                "}",
                RuntimeTypes.Serde.SerdeXml.XmlTagReader,
            ) {
                withBlock(
                    "if (root.tagName != #S) {",
                    "}",
                    "Response",
                ) {
                    write("throw #T(#S)", RuntimeTypes.Serde.DeserializationException, "invalid root, expected <Response>; found `\${root.tag}`")
                }

                write("val errorsTag = root.nextTag()")
                withBlock(
                    "if (errorsTag == null || errorsTag.tagName != #S) {",
                    "}",
                    "Errors",
                ) {
                    write("throw #T(#S)", RuntimeTypes.Serde.DeserializationException, "invalid error, expected <Errors>; found `\${errorsTag?.tag}`")
                }

                write("val errTag = errorsTag.nextTag()")
                withBlock(
                    "if (errTag == null || errTag.tagName != #S) {",
                    "}",
                    "Error",
                ) {
                    write("throw #T(#S)", RuntimeTypes.Serde.DeserializationException, "invalid error, expected <Error>; found `\${errTag?.tag}`")
                }

                write("return errTag")
            }
        }
    }
}

/**
 * An EC2 Query implementation of [SerializeStructGenerator] which ensures that empty lists are not serialized.
 */
private class Ec2QuerySerializeStructGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    members: List<MemberShape>,
    writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format,
) : SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat) {
    override fun renderListMemberSerializer(memberShape: MemberShape, targetShape: CollectionShape) {
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        val descriptorName = memberShape.descriptorName()
        val nestingLevel = 0
        val memberSymbol = ctx.symbolProvider.toSymbol(memberShape)

        if (memberSymbol.isNullable) {
            writer.withBlock("if (!input.$memberName.isNullOrEmpty()) {", "}") {
                writer.withBlock("listField($descriptorName) {", "}") {
                    delegateListSerialization(memberShape, targetShape, nestingLevel, memberName)
                }
            }
        } else {
            writer.withBlock("if (input.$memberName.isNotEmpty()) {", "}") {
                writer.withBlock("listField($descriptorName) {", "}") {
                    delegateListSerialization(memberShape, targetShape, nestingLevel, memberName)
                }
            }
        }
    }
}
