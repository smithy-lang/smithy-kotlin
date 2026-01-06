/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.aws.protocols.core

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import aws.smithy.kotlin.codegen.model.targetOrSelf
import aws.smithy.kotlin.codegen.protocols.core.StaticHttpBindingResolver
import aws.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.HttpBindingResolver
import aws.smithy.kotlin.codegen.rendering.protocol.MutateHeadersMiddleware
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import aws.smithy.kotlin.codegen.rendering.serde.FormUrlSerdeDescriptorGenerator
import aws.smithy.kotlin.codegen.rendering.serde.SerializeStructGenerator
import aws.smithy.kotlin.codegen.rendering.serde.SerializeUnionGenerator
import aws.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import aws.smithy.kotlin.codegen.rendering.serde.bodySerializer
import aws.smithy.kotlin.codegen.rendering.serde.bodySerializerName
import aws.smithy.kotlin.codegen.rendering.serde.documentSerializer
import aws.smithy.kotlin.codegen.rendering.serde.payloadSerializer
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.pattern.UriPattern
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

private const val QUERY_CONTENT_TYPE: String = "application/x-www-form-urlencoded"

abstract class QueryHttpBindingProtocolGenerator : HttpBindingProtocolGenerator() {
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.DATE_TIME

    override fun getDefaultHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> {
        val middleware = super.getDefaultHttpMiddleware(ctx)

        val queryMiddleware = listOf(
            // ensure content-type gets set
            // see: https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#protocol-behavior
            MutateHeadersMiddleware(addMissingHeaders = mapOf("Content-Type" to QUERY_CONTENT_TYPE)),
        )

        return middleware + queryMiddleware
    }

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        QueryBindingResolver(model, serviceShape)

    override fun renderSerializeHttpBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        val input = ctx.model.expectShape<StructureShape>(op.input.get())
        if (input.members().isEmpty()) {
            // if there is no payload serialized we still need to add the literals that define the operation being
            // invoked
            // see: https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#request-serialization
            val action = op.id.name
            val service = ctx.model.expectShape<ServiceShape>(ctx.settings.service)
            val version = service.version
            writer.write("""val content = "Action=$action&Version=$version"""")
            writer.write("builder.body = #T.fromBytes(content.encodeToByteArray())", RuntimeTypes.Http.HttpBody)
        } else {
            super.renderSerializeHttpBody(ctx, op, writer)
        }
    }
}

/**
 * An HTTP binding resolver for the query binding protocols
 */
class QueryBindingResolver(
    model: Model,
    service: ServiceShape,
) : StaticHttpBindingResolver(model, service, QueryHttpTrait, QUERY_CONTENT_TYPE, TimestampFormatTrait.Format.DATE_TIME) {
    constructor(ctx: ProtocolGenerator.GenerationContext) : this(ctx.model, ctx.service)

    companion object {
        val QueryHttpTrait: HttpTrait = HttpTrait
            .builder()
            .code(200)
            .method("POST")
            .uri(UriPattern.parse("/"))
            .build()
    }
}

abstract class AbstractQueryFormUrlSerializerGenerator(
    private val protocolGenerator: ProtocolGenerator,
    private val defaultTimestampFormat: TimestampFormatTrait.Format,
) : StructuredDataSerializerGenerator {

    abstract fun descriptorGenerator(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ): FormUrlSerdeDescriptorGenerator

    override fun operationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol {
        val input = op.input.get().let { ctx.model.expectShape(it) }
        val symbol = ctx.symbolProvider.toSymbol(input)

        return op.bodySerializer(ctx.settings) { writer ->
            addNestedDocumentSerializers(ctx, op, writer)
            val fnName = op.bodySerializerName()
            writer.openBlock("private fun #L(context: #T, input: #T): ByteArray {", fnName, RuntimeTypes.Core.ExecutionContext, symbol)
                .call {
                    renderSerializeOperationBody(ctx, op, members, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Register nested structure/map shapes reachable from the operation input shape that require a "document" serializer
     * implementation
     */
    private fun addNestedDocumentSerializers(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter) {
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDocumentSerializer = serdeIndex.requiresDocumentSerializer(shape)
        // register a dependency on each of the members that require a serializer impl
        // ensuring they get generated
        shapesRequiringDocumentSerializer.forEach {
            val nestedStructOrUnionSerializer = documentSerializer(ctx, it)
            writer.addImport(nestedStructOrUnionSerializer)
        }
    }

    private fun renderSerializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        documentMembers: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        val shape = ctx.model.expectShape(op.input.get())
        writer.write("val serializer = #T()", RuntimeTypes.Serde.SerdeFormUrl.FormUrlSerializer)
        renderSerializerBody(ctx, shape, documentMembers, writer)
        writer.write("return serializer.toByteArray()")
    }

    private fun documentSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape> = shape.members(),
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        return shape.documentSerializer(ctx.settings, symbol, members) { writer ->
            writer.openBlock("internal fun #identifier.name:L(serializer: #T, input: #T) {", RuntimeTypes.Serde.Serializer, symbol)
                .call {
                    renderSerializerBody(ctx, shape, members.toList(), writer)
                }
                .closeBlock("}")
        }
    }

    open fun renderSerializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        // render the serde descriptors
        descriptorGenerator(ctx, shape, members, writer).render()
        when (shape) {
            is UnionShape -> SerializeUnionGenerator(ctx, shape, members, writer, defaultTimestampFormat).render()
            else -> SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat).render()
        }
    }

    override fun payloadSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape>?,
    ): Symbol {
        // re-use document serializer (for the target shape!)
        val target = shape.targetOrSelf(ctx.model)
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val forMembers = members ?: target.members()

        val serializeFn = documentSerializer(ctx, target, forMembers)
        return target.payloadSerializer(ctx.settings, symbol, forMembers) { writer ->
            addNestedDocumentSerializers(ctx, target, writer)
            writer.withBlock("internal fun #identifier.name:L(input: #T): ByteArray {", "}", symbol) {
                write("val serializer = #T()", RuntimeTypes.Serde.SerdeFormUrl.FormUrlSerializer)
                write("#T(serializer, input)", serializeFn)
                write("return serializer.toByteArray()")
            }
        }
    }
}
