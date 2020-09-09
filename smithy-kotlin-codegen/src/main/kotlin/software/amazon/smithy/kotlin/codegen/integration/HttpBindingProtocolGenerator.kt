/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen.integration

import java.util.logging.Logger
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.neighbor.RelationshipType
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*
import software.amazon.smithy.utils.StringUtils

// TODO - figure out how much of this needs to be refactored and moved to specific protocol generators
// e.g. restJson. Some of the details of how serializers are generated are definitely protocol specific
// and need to be moved out of this implementation or hooks added to customize.

/**
 * Abstract implementation useful for all HTTP protocols
 */
abstract class HttpBindingProtocolGenerator : ProtocolGenerator {
    private val LOGGER = Logger.getLogger(javaClass.name)

    override val applicationProtocol: ApplicationProtocol = ApplicationProtocol.createDefaultHttpApplicationProtocol()

    /**
     * The default serde format for timestamps.
     */
    protected abstract val defaultTimestampFormat: TimestampFormatTrait.Format

    /**
     * The default content-type when a document is synthesized in the body.
     */
    protected abstract val defaultContentType: String

    /**
     * Get all of the features that should be installed into the `SdkHttpClient` as pipeline middleware
     */
    open fun getHttpFeatures(ctx: ProtocolGenerator.GenerationContext): List<HttpFeature> = listOf()

    override fun generateSerializers(ctx: ProtocolGenerator.GenerationContext) {
        // render HttpSerialize for all operation inputs
        for (operation in getHttpBindingOperations(ctx)) {
            generateOperationSerializer(ctx, operation)
        }

        // generate serde for all shapes that appear as nested on any operation input
        // these types are `SdkSerializable` not `HttpSerialize`
        val shapesRequiringSerializers = resolveRequiredSerializers(ctx)
        generateDocumentSerializers(ctx, shapesRequiringSerializers)
    }

    override fun generateDeserializers(ctx: ProtocolGenerator.GenerationContext) {
        // render HttpDeserialize for all operation outputs
        val httpOperations = getHttpBindingOperations(ctx)
        for (operation in httpOperations) {
            generateOperationDeserializer(ctx, operation)
        }

        // generate HttpDeserialize for exception types
        val modeledErrors = httpOperations.flatMap { it.errors }.map { ctx.model.expectShape(it) as StructureShape }.toSet()
        modeledErrors.forEach { generateExceptionDeserializer(ctx, it) }

        // generate serde for all shapes that appear as nested on any operation output
        // these types are independent document deserializers, they do not implement `HttpDeserialize`
        val shapesRequiringDeserializers = resolveRequiredDeserializers(ctx)
        generateDocumentDeserializers(ctx, shapesRequiringDeserializers)
    }

    override fun generateProtocolClient(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = ctx.symbolProvider.toSymbol(ctx.service)
        val rootNamespace = ctx.settings.moduleName
        ctx.delegator.useFileWriter("Default${symbol.name}.kt", rootNamespace) { writer ->
            val features = getHttpFeatures(ctx)
            HttpProtocolClientGenerator(
                ctx.model,
                ctx.symbolProvider,
                writer,
                ctx.service,
                rootNamespace,
                features
            ).render()
        }
    }

    /**
     * Get the operations with HTTP Bindings.
     *
     * @param ctx the generation context
     * @return the list of operation shapes
     */
    open fun getHttpBindingOperations(ctx: ProtocolGenerator.GenerationContext): List<OperationShape> {
        val topDownIndex: TopDownIndex = ctx.model.getKnowledge(TopDownIndex::class.java)

        return topDownIndex.getContainedOperations(ctx.service)
            .filter { op ->
                val hasHttpTrait = op.hasTrait(HttpTrait::class.java)
                if (!hasHttpTrait) {
                    LOGGER.warning(
                        "Unable to fetch $protocol protocol request bindings for ${op.id} because " +
                                "it does not have an http binding trait"
                    )
                }
                hasHttpTrait
            }.toList<OperationShape>()
    }

    /**
     * Find and return the set of shapes that are not operation inputs but do require a serializer
     *
     * Operation inputs get an implementation of `HttpSerialize`, everything else gets an implementation
     * of `SdkSerializable`.
     *
     * @return The set of shapes that require a serializer implementation
     */
    private fun resolveRequiredSerializers(ctx: ProtocolGenerator.GenerationContext): Set<Shape> {
        // all top level operation inputs get an HttpSerialize
        // any structure or union shape that shows up as a nested member (direct or indirect)
        // as well as collections of the same requires a serializer implementation though
        val topLevelMembers = getHttpBindingOperations(ctx)
            .filter { it.input.isPresent }
            .flatMap {
                val inputShape = ctx.model.expectShape(it.input.get())
                inputShape.members()
            }
            .map { ctx.model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toSet()

        return walkNestedShapesRequiringSerde(ctx, topLevelMembers)
    }

    /**
     * Find and return the set of shapes that are not operation outputs but do require a deserializer
     *
     * Operation outputs get an implementation of `HttpDeserialize`, everything else gets a `deserialize()`
     * implementation
     *
     * @return The set of shapes that require a deserializer implementation
     */
    private fun resolveRequiredDeserializers(ctx: ProtocolGenerator.GenerationContext): Set<Shape> {
        // All top level operation outputs and errors get an HttpDeserialize implementation.
        // Any structure or union shape that shows up as a nested member, direct or indirect (of either the operation
        // or it's errors), as well as collections of the same requires a deserializer implementation though.

        // add shapes reachable from the operational output itself
        val topLevelMembers = getHttpBindingOperations(ctx)
            .filter { it.output.isPresent }
            .flatMap {
                val outputShape = ctx.model.expectShape(it.output.get())
                outputShape.members()
            }
            .map { ctx.model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toMutableSet()

        // add shapes reachable from operational errors
        val modeledErrors = getHttpBindingOperations(ctx)
            .flatMap { it.errors }
            .flatMap { ctx.model.expectShape(it).members() }
            .map { ctx.model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toSet()

        topLevelMembers += modeledErrors

        return walkNestedShapesRequiringSerde(ctx, topLevelMembers)
    }

    private fun walkNestedShapesRequiringSerde(ctx: ProtocolGenerator.GenerationContext, shapes: Set<Shape>): Set<Shape> {
        val resolved = mutableSetOf<Shape>()
        val walker = Walker(ctx.model)

        // walk all the shapes in the set and find all other
        // structs/unions (or collections thereof) in the graph from that shape
        shapes.forEach { shape ->
            walker.iterateShapes(shape) { relationship ->
                when (relationship.relationshipType) {
                    RelationshipType.MEMBER_TARGET,
                    RelationshipType.STRUCTURE_MEMBER,
                    RelationshipType.LIST_MEMBER,
                    RelationshipType.SET_MEMBER,
                    RelationshipType.MAP_VALUE,
                    RelationshipType.UNION_MEMBER -> true
                    else -> false
                }
            }.forEach { walkedShape ->
                if (walkedShape.type == ShapeType.STRUCTURE || walkedShape.type == ShapeType.UNION) {
                    resolved.add(walkedShape)
                }
            }
        }
        return resolved
    }

    /**
     * Generate `SdkSerializable` serializer for all shapes in the set
     */
    private fun generateDocumentSerializers(ctx: ProtocolGenerator.GenerationContext, shapes: Set<Shape>) {
        for (shape in shapes) {
            val symbol = ctx.symbolProvider.toSymbol(shape)
            // serializer class for the shape takes the shape's symbol as input
            // ensure we get an import statement to the symbol from the .model package
            val reference = SymbolReference.builder()
                .symbol(symbol)
                .options(SymbolReference.ContextOption.DECLARE)
                .build()
            val serializerSymbol = Symbol.builder()
                .definitionFile("${symbol.name}Serializer.kt")
                .name("${symbol.name}Serializer")
                .namespace("${ctx.settings.moduleName}.transform", ".")
                .addReference(reference)
                .build()

            ctx.delegator.useShapeWriter(serializerSymbol) { writer ->
                renderDocumentSerializer(ctx, symbol, shape, serializerSymbol, writer)
            }
        }
    }

    /**
     * Actually renders the `SdkSerializable` implementation for the given symbol/shape
     * @param ctx The codegen context
     * @param symbol The symbol to generate a serializer implementation for
     * @param shape The corresponding shape
     * @param serializerSymbol The symbol for the serializer class that wraps the [symbol]
     * @param writer The codegen writer to render to
     */
    private fun renderDocumentSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        symbol: Symbol,
        shape: Shape,
        serializerSymbol: Symbol,
        writer: KotlinWriter
    ) {
        importSerdePackage(writer)

        writer.write("")
            .openBlock("class \$L(val input: \$L) : SdkSerializable {", serializerSymbol.name, symbol.name)
            .call {
                renderSerdeCompanionObject(shape.members().toList(), writer)
            }
            .call {
                writer.withBlock("override fun serialize(serializer: Serializer) {", "}") {
                    SerializeStructGenerator(ctx, shape.members().toList(), writer, defaultTimestampFormat).render()
                }
            }
            .closeBlock("}")
    }

    /**
     * Generate request serializer (HttpSerialize) for an operation
     */
    private fun generateOperationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape) {
        if (!op.input.isPresent) {
            return
        }

        val inputShape = ctx.model.expectShape(op.input.get())
        val inputSymbol = ctx.symbolProvider.toSymbol(inputShape)

        val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
        val ref = SymbolReference.builder()
            .symbol(inputSymbol)
            .options(SymbolReference.ContextOption.DECLARE)
            .build()

        // operation input shapes could be re-used across one or more operations. The protocol details may
        // be different though (e.g. uri/method). We need to generate a serializer/deserializer per/operation
        // NOT per input/output shape
        val serializerSymbol = Symbol.builder()
            .definitionFile("${op.serializerName()}.kt")
            .name(op.serializerName())
            .namespace("${ctx.settings.moduleName}.transform", ".")
            .addReference(ref)
            .build()

        val httpTrait = op.expectTrait(HttpTrait::class.java)
        val requestBindings = bindingIndex.getRequestBindings(op)
        ctx.delegator.useShapeWriter(serializerSymbol) { writer ->
            // import all of http, http.request, and serde packages. All serializers requires one or more of the symbols
            // and most require quite a few. Rather than try and figure out which specific ones are used just take them
            // all to ensure all the various DSL builders are available, etc
            importSerdePackage(writer)
            writer.addImport(KotlinDependency.CLIENT_RT_HTTP.namespace, "*", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.request", "*", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.feature", "HttpSerialize", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.feature", "SerializationProvider", "")

            writer.write("")
                .openBlock("class \$L(val input: \$L) : HttpSerialize {", op.serializerName(), inputSymbol.name)
                .call {
                    val memberShapes = requestBindings.values.filter { it.location == HttpBinding.Location.DOCUMENT }.map { it.member }
                    renderSerdeCompanionObject(memberShapes, writer)
                }
                .call {
                    val contentType =
                        bindingIndex.determineRequestContentType(op, defaultContentType).orElse(defaultContentType)
                    renderHttpSerialize(ctx, httpTrait, contentType, requestBindings, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Generate the field descriptors
     */
    private fun renderSerdeCompanionObject(members: List<MemberShape>, writer: KotlinWriter) {
        if (members.isEmpty()) return
        // TODO - this is a really simplified version, we may need to allow hooks for protocol generators to control this better
        writer.write("")
            .withBlock("companion object {", "}") {
                val sortedMembers = members.sortedBy { it.memberName }
                for (member in sortedMembers) {
                    val serialName = member.getTrait(JsonNameTrait::class.java).map { it.value }.orElse(member.memberName)
                    write("private val \$L = SdkFieldDescriptor(\"\$L\")", member.descriptorName(), serialName)
                }
                writer.withBlock("private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {", "}") {
                    for (member in sortedMembers) {
                        write("field(\$L)", member.descriptorName())
                    }
                }
            }
            .write("")
    }

    // replace labels with any path bindings
    private fun resolveUriPath(
        ctx: ProtocolGenerator.GenerationContext,
        httpTrait: HttpTrait,
        pathBindings: List<HttpBinding>,
        writer: KotlinWriter
    ): String {
        return httpTrait.uri.segments.joinToString(
            separator = "/",
            prefix = "/",
            postfix = "",
            transform = { segment ->
                if (segment.isLabel) {
                    // spec dictates member name and label name MUST be the same
                    val binding = pathBindings.find { binding ->
                        binding.memberName == segment.content
                    } ?: throw CodegenException("failed to find corresponding member for httpLabel `${segment.content}")

                    // shape must be string, number, boolean, or timestamp
                    val targetShape = ctx.model.expectShape(binding.member.target)
                    if (targetShape.isTimestampShape) {
                        importTimestampFormat(writer)
                        val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
                        val tsFormat = bindingIndex.determineTimestampFormat(
                            binding.member,
                            HttpBinding.Location.LABEL,
                            defaultTimestampFormat
                        )
                        val tsLabel = formatInstant("input.${binding.member.defaultName()}?", tsFormat, forceString = true)
                        "\${$tsLabel}"
                    } else {
                        "\${input.${binding.member.defaultName()}}"
                    }
                } else {
                    // literal
                    segment.content
                }
            }
        )
    }

    private fun renderHttpSerialize(
        ctx: ProtocolGenerator.GenerationContext,
        httpTrait: HttpTrait,
        contentType: String,
        requestBindings: Map<String, HttpBinding>,
        writer: KotlinWriter
    ) {
        writer.openBlock("override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {")
            .write("builder.method = HttpMethod.\$L", httpTrait.method.toUpperCase())
            .write("")
            .call {
                // URI components
                val pathBindings = requestBindings.values.filter { it.location == HttpBinding.Location.LABEL }
                val queryBindings = requestBindings.values.filter { it.location == HttpBinding.Location.QUERY }
                val resolvedPath = resolveUriPath(ctx, httpTrait, pathBindings, writer)

                writer.withBlock("builder.url {", "}") {
                    // Path
                    write("path = \"\$L\"", resolvedPath)

                    // Query Parameters
                    renderQueryParameters(ctx, httpTrait.uri.queryLiterals, queryBindings, writer)
                }
            }
            .write("")
            .call {
                // headers
                val headerBindings = requestBindings.values
                    .filter { it.location == HttpBinding.Location.HEADER }
                    .sortedBy { it.memberName }

                val prefixHeaderBindings = requestBindings.values
                    .filter { it.location == HttpBinding.Location.PREFIX_HEADERS }

                writer.withBlock("builder.headers {", "}") {
                    write("append(\"Content-Type\", \"\$L\")", contentType)
                    renderStringValuesMapParameters(ctx, headerBindings, writer)
                    prefixHeaderBindings.forEach {
                        writer.withBlock("input.${it.member.defaultName()}?.forEach { (key, value) ->", "}") {
                            write("append(\"\$L\$\$key\", value)", it.locationName)
                        }
                    }
                }
            }
            .write("")
            .call {
                // payload member(s)
                val httpPayload = requestBindings.values.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
                if (httpPayload != null) {
                    renderExplicitHttpPayloadSerializer(ctx, httpPayload, writer)
                } else {
                    // Unbound document members that should be serialized into the document format for the protocol.
                    // The generated code is the same across protocols and the serialization provider instance
                    // passed into the function is expected to handle the formatting required by the protocol
                    val documentMembers = requestBindings.values
                        .filter { it.location == HttpBinding.Location.DOCUMENT }
                        .sortedBy { it.memberName }

                    renderUnboundPayloadSerde(ctx, documentMembers, writer)
                }
            }
            .closeBlock("}")
    }

    private fun renderQueryParameters(
        ctx: ProtocolGenerator.GenerationContext,
        // literals in the URI
        queryLiterals: Map<String, String>,
        // shape bindings
        queryBindings: List<HttpBinding>,
        writer: KotlinWriter
    ) {

        if (queryBindings.isEmpty() && queryLiterals.isEmpty()) return

        writer.withBlock("parameters {", "}") {
            queryLiterals.forEach { (key, value) ->
                writer.write("append(\$S, \$S)", key, value)
            }
            renderStringValuesMapParameters(ctx, queryBindings, writer)
        }
    }

    // shared implementation for rendering members that belong to StringValuesMap (e.g. Header or Query parameters)
    private fun renderStringValuesMapParameters(
        ctx: ProtocolGenerator.GenerationContext,
        bindings: List<HttpBinding>,
        writer: KotlinWriter
    ) {
        val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
        bindings.forEach {
            var memberName = it.member.defaultName()
            val memberTarget = ctx.model.expectShape(it.member.target)
            val paramName = it.locationName
            val location = it.location
            val member = it.member
            when (memberTarget) {
                is CollectionShape -> {
                    val collectionMemberTarget = ctx.model.expectShape(memberTarget.member.target)
                    val mapFnContents = when (collectionMemberTarget.type) {
                        ShapeType.TIMESTAMP -> {
                            // special case of timestamp list
                            val tsFormat = bindingIndex.determineTimestampFormat(member, location, defaultTimestampFormat)
                            importTimestampFormat(writer)
                            // headers/query params need to be a string
                            formatInstant("it", tsFormat, forceString = true)
                        }
                        ShapeType.STRING -> {
                            if (collectionMemberTarget.hasTrait(EnumTrait::class.java)) {
                                // collections of enums should be mapped to the raw values
                                "it.value"
                            } else {
                                // collections of string doesn't need mapped to anything
                                ""
                            }
                        }
                        // default to "toString"
                        else -> "\"\$it\""
                    }

                    // appendAll collection parameter 2
                    val param2 = if (mapFnContents.isEmpty()) "input.$memberName" else "input.$memberName.map { $mapFnContents }"
                    writer.write(
                        "if (input.\$1L?.isNotEmpty() == true) appendAll(\"\$2L\", \$3L)",
                        memberName,
                        paramName,
                        param2
                    )
                }
                is TimestampShape -> {
                    val tsFormat = bindingIndex.determineTimestampFormat(member, location, defaultTimestampFormat)
                    // headers/query params need to be a string
                    val formatted = formatInstant("input.$memberName", tsFormat, forceString = true)
                    writer.write("if (input.\$1L != null) append(\"\$2L\", \$3L)", memberName, paramName, formatted)
                    importTimestampFormat(writer)
                }
                is BlobShape -> {
                    importBase64Utils(writer)
                    writer.write(
                        "if (input.\$1L?.isNotEmpty() == true) append(\"\$2L\", input.\$1L.encodeBase64String())",
                        memberName,
                        paramName
                    )
                }
                is StringShape -> {
                    // NOTE: query parameters are allowed to be empty, whereas headers should omit empty string
                    // values from serde
                    val cond =
                        if (location == HttpBinding.Location.QUERY || memberTarget.hasTrait(EnumTrait::class.java)) {
                            "input.$memberName != null"
                        } else {
                            "input.$memberName?.isNotEmpty() == true"
                        }

                    val suffix = when {
                        memberTarget.hasTrait(EnumTrait::class.java) -> {
                            ".value"
                        }
                        memberTarget.hasTrait(MediaTypeTrait::class.java) -> {
                            importBase64Utils(writer)
                            ".encodeBase64()"
                        }
                        else -> ""
                    }

                    writer.write("if (\$1L) append(\"\$2L\", \$3L)", cond, paramName, "input.${memberName}$suffix")
                }
                else -> {
                    // encode to string
                    val encodedValue = "\"\${input.$memberName}\""
                    writer.write("if (input.\$1L != null) append(\"\$2L\", \$3L)", memberName, paramName, encodedValue)
                }
            }
        }
    }

    /**
     * Render serialization for member(s) not bound to any other http traits, these members are serialized using
     * the protocol specific document format
     *
     * @param ctx The code generation context
     * @param members The document members to serialize
     * @param writer The code writer to render to
     */
    private fun renderUnboundPayloadSerde(
        ctx: ProtocolGenerator.GenerationContext,
        members: List<HttpBinding>,
        writer: KotlinWriter
    ) {
        if (members.isEmpty()) return

        writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.content", "ByteArrayContent", "")
        writer.write("val serializer = provider()")
            .call {
                val renderForMembers = members.map { it.member }
                SerializeStructGenerator(ctx, renderForMembers, writer, defaultTimestampFormat).render()
            }
            .write("")
            .write("builder.body = ByteArrayContent(serializer.toByteArray())")
    }

    /**
     * Render serialization for a member bound with the `httpPayload` trait
     *
     * @param ctx The code generation context
     * @param binding The explicit payload binding
     * @param writer The code writer to render to
     */
    private fun renderExplicitHttpPayloadSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        binding: HttpBinding,
        writer: KotlinWriter
    ) {
        // explicit payload member as the sole payload
        val memberName = binding.member.defaultName()
        writer.openBlock("if (input.\$L != null) {", memberName)

        val target = ctx.model.expectShape(binding.member.target)

        when (target.type) {
            ShapeType.BLOB -> {
                val isBinaryStream = ctx.model.getShape(binding.member.target).get().hasTrait(StreamingTrait::class.java)
                if (isBinaryStream) {
                    writer.write("builder.body = input.\$L.toHttpBody() ?: HttpBody.Empty", memberName)
                } else {
                    writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.content", "ByteArrayContent", "")
                    writer.write("builder.body = ByteArrayContent(input.\$L)", memberName)
                }
            }
            ShapeType.STRING -> {
                writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.content", "ByteArrayContent", "")
                val contents = if (target.hasTrait(EnumTrait::class.java)) {
                    "$memberName.value"
                } else {
                    memberName
                }
                writer.write("builder.body = ByteArrayContent(input.\$L.toByteArray())", contents)
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                // delegate to the member serializer
                writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.content", "ByteArrayContent", "")
                val memberSymbol = ctx.symbolProvider.toSymbol(binding.member)
                writer.write("val serializer = provider()")
                    .write("\$LSerializer(input.\$L).serialize(serializer)", memberSymbol.name, memberName)
                    .write("builder.body = ByteArrayContent(serializer.toByteArray())")
            }
            ShapeType.DOCUMENT -> {
                // TODO - deal with document members
            }
            else -> throw CodegenException("member shape ${binding.member} serializer not implemented yet")
        }
        writer.closeBlock("}")
    }

    /**
     * Generate request deserializer (HttpDeserialize) for an operation
     */
    private fun generateOperationDeserializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape) {
        if (!op.output.isPresent) {
            return
        }

        val outputShape = ctx.model.expectShape(op.output.get())
        val outputSymbol = ctx.symbolProvider.toSymbol(outputShape)

        val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
        val ref = SymbolReference.builder()
            .symbol(outputSymbol)
            .options(SymbolReference.ContextOption.DECLARE)
            .build()

        // operation output shapes could be re-used across one or more operations. The protocol details may
        // be different though (e.g. uri/method). We need to generate a serializer/deserializer per/operation
        // NOT per input/output shape
        val deserializerSymbol = Symbol.builder()
            .definitionFile("${op.deserializerName()}.kt")
            .name(op.deserializerName())
            .namespace("${ctx.settings.moduleName}.transform", ".")
            .addReference(ref)
            .build()

        val responseBindings = bindingIndex.getResponseBindings(op)
        ctx.delegator.useShapeWriter(deserializerSymbol) { writer ->
            // import all of http, http.response , and serde packages. All serializers requires one or more of the symbols
            // and most require quite a few. Rather than try and figure out which specific ones are used just take them
            // all to ensure all the various DSL builders are available, etc
            importSerdePackage(writer)
            writer.addImport(KotlinDependency.CLIENT_RT_HTTP.namespace, "*", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.response", "HttpResponse", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.feature", "HttpDeserialize", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.feature", "DeserializationProvider", "")

            writer.write("")
                .openBlock("class \$L : HttpDeserialize {", op.deserializerName())
                .write("")
                .call {
                    val memberShapes = responseBindings.values
                        .filter { it.location == HttpBinding.Location.DOCUMENT }
                        .map { it.member }
                    renderSerdeCompanionObject(memberShapes, writer)
                }
                .write("")
                .call {
                    renderHttpDeserialize(ctx, outputSymbol, responseBindings, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Generate HttpDeserialize for a modeled error (exception)
     */
    private fun generateExceptionDeserializer(ctx: ProtocolGenerator.GenerationContext, shape: StructureShape) {
        val outputSymbol = ctx.symbolProvider.toSymbol(shape)

        val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
        val ref = SymbolReference.builder()
            .symbol(outputSymbol)
            .options(SymbolReference.ContextOption.DECLARE)
            .build()

        val deserializerName = "${outputSymbol.name}Deserializer"
        val deserializerSymbol = Symbol.builder()
            .definitionFile("$deserializerName.kt")
            .name(deserializerName)
            .namespace("${ctx.settings.moduleName}.transform", ".")
            .addReference(ref)
            .build()

        ctx.delegator.useShapeWriter(deserializerSymbol) { writer ->
            importSerdePackage(writer)
            writer.addImport(KotlinDependency.CLIENT_RT_HTTP.namespace, "*", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.response", "HttpResponse", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.feature", "HttpDeserialize", "")
            writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.feature", "DeserializationProvider", "")

            writer.write("")
                .openBlock("class \$L : HttpDeserialize {", deserializerName)
                .write("")
                .call {
                    val documentMembers = shape.members().filterNot {
                        it.hasTrait(HttpHeaderTrait::class.java) || it.hasTrait(HttpPrefixHeadersTrait::class.java)
                    }

                    renderSerdeCompanionObject(documentMembers, writer)
                }
                .write("")
                .call {
                    val responseBindings = bindingIndex.getResponseBindings(shape)
                    renderHttpDeserialize(ctx, outputSymbol, responseBindings, writer)
                }
                .closeBlock("}")
        }
    }

    private fun renderHttpDeserialize(
        ctx: ProtocolGenerator.GenerationContext,
        outputSymbol: Symbol,
        responseBindings: Map<String, HttpBinding>,
        writer: KotlinWriter
    ) {
        writer.openBlock(
            "override suspend fun deserialize(response: HttpResponse, provider: DeserializationProvider): \$L {",
            outputSymbol.name
        )
            .write("val builder = ${outputSymbol.name}.dslBuilder()")
            .write("")
            .call {
                // headers
                val headerBindings = responseBindings.values
                    .filter { it.location == HttpBinding.Location.HEADER }
                    .sortedBy { it.memberName }

                renderDeserializeHeaders(ctx, headerBindings, writer)

                // prefix headers
                // spec: "Only a single structure member can be bound to httpPrefixHeaders"
                responseBindings.values.firstOrNull { it.location == HttpBinding.Location.PREFIX_HEADERS }
                    ?.let {
                        renderDeserializePrefixHeaders(ctx, it, writer)
                    }
            }
            .write("")
            .call {
                // document members
                // payload member(s)
                val httpPayload = responseBindings.values.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
                if (httpPayload != null) {
                    renderExplicitHttpPayloadDeserializer(ctx, httpPayload, writer)
                } else {
                    // Unbound document members that should be deserialized from the document format for the protocol.
                    // The generated code is the same across protocols and the serialization provider instance
                    // passed into the function is expected to handle the formatting required by the protocol
                    val documentMembers = responseBindings.values
                        .filter { it.location == HttpBinding.Location.DOCUMENT }
                        .sortedBy { it.memberName }
                        .map { it.member }

                    if (documentMembers.isNotEmpty()) {
                        writer.write("val payload = response.body.readAll()")
                        writer.withBlock("if (payload != null) {", "}") {
                            writer.write("val deserializer = provider(payload)")
                            DeserializeStructGenerator(ctx, documentMembers, writer, defaultTimestampFormat).render()
                        }
                    }
                }
            }
            .write("return builder.build()")
            .closeBlock("}")
    }

    /**
     * Render deserialization of all members bound to a response header
     */
    private fun renderDeserializeHeaders(
        ctx: ProtocolGenerator.GenerationContext,
        bindings: List<HttpBinding>,
        writer: KotlinWriter
    ) {
        bindings.forEach { hdrBinding ->
            val memberTarget = ctx.model.expectShape(hdrBinding.member.target)
            val memberName = hdrBinding.member.defaultName()
            val headerName = hdrBinding.locationName
            when (memberTarget) {
                is NumberShape -> {
                    writer.write(
                        "builder.\$L = response.headers[\$S]?.\$L",
                        memberName, headerName, stringToNumber(memberTarget)
                    )
                }
                is BlobShape -> {
                    importBase64Utils(writer)
                    writer.write("builder.\$L = response.headers[\$S]?.decodeBase64()", memberName, headerName)
                }
                is BooleanShape -> {
                    writer.write("builder.\$L = response.headers[\$S]?.toBoolean()", memberName, headerName)
                }
                is StringShape -> {
                    when {
                        memberTarget.hasTrait(EnumTrait::class.java) -> {
                            val enumSymbol = ctx.symbolProvider.toSymbol(memberTarget)
                            writer.addImport(enumSymbol, "", SymbolReference.ContextOption.DECLARE)
                            writer.write(
                                "builder.\$L = response.headers[\$S]?.let { \$L.fromValue(it) }",
                                memberName,
                                headerName,
                                enumSymbol.name
                            )
                        }
                        memberTarget.hasTrait(MediaTypeTrait::class.java) -> {
                            importBase64Utils(writer)
                            writer.write("builder.\$L = response.headers[\$S]?.decodeBase64()", memberName, headerName)
                        }
                        else -> {
                            writer.write("builder.\$L = response.headers[\$S]", memberName, headerName)
                        }
                    }
                }
                is TimestampShape -> {
                    val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
                    val tsFormat = bindingIndex.determineTimestampFormat(
                        hdrBinding.member,
                        HttpBinding.Location.HEADER,
                        defaultTimestampFormat)
                    importInstant(writer)

                    writer.write("builder.\$L = response.headers[\$S]?.let { \$L }",
                        memberName, headerName, parseInstant("it", tsFormat))
                }
                is CollectionShape -> {
                    // member > boolean, number, string, or timestamp
                    // headers are List<String>, get the internal mapping function contents (if any) to convert
                    // to the target symbol type

                    // we also have to handle multiple comma separated values (e.g. 'X-Foo': "1, 2, 3"`)
                    var splitFn = "splitHeaderListValues"
                    val conversion = when (val collectionMemberTarget = ctx.model.expectShape(memberTarget.member.target)) {
                        is BooleanShape -> "it.toBoolean()"
                        is NumberShape -> "it." + stringToNumber(collectionMemberTarget)
                        is TimestampShape -> {
                            val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
                            val tsFormat = bindingIndex.determineTimestampFormat(
                                hdrBinding.member,
                                HttpBinding.Location.HEADER,
                                defaultTimestampFormat)
                            if (tsFormat == TimestampFormatTrait.Format.HTTP_DATE) {
                                splitFn = "splitHttpDateHeaderListValues"
                            }
                            importInstant(writer)
                            parseInstant("it", tsFormat)
                        }
                        is StringShape -> {
                            when {
                                collectionMemberTarget.hasTrait(EnumTrait::class.java) -> {
                                    val enumSymbol = ctx.symbolProvider.toSymbol(collectionMemberTarget)
                                    writer.addImport(enumSymbol, "", SymbolReference.ContextOption.DECLARE)
                                    "${enumSymbol.name}.fromValue(it)"
                                }
                                collectionMemberTarget.hasTrait(MediaTypeTrait::class.java) -> {
                                    importBase64Utils(writer)
                                    "it.decodeBase64()"
                                }
                                else -> ""
                            }
                        }
                        else -> throw CodegenException("invalid member type for header collection: binding: $hdrBinding; member: $memberName")
                    }

                    val toCollectionType = when {
                        memberTarget.isListShape -> ""
                        memberTarget.isSetShape -> "?.toSet()"
                        else -> throw CodegenException("unknown collection shape: $memberTarget")
                    }

                    val mapFn = if (conversion.isNotEmpty()) "?.map { $conversion }" else ""

                    writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.util", splitFn, "")
                    writer.write("builder.\$L = response.headers.getAll(\$S)?.flatMap(::$splitFn)${mapFn}$toCollectionType", memberName, headerName)
                }
                else -> throw CodegenException("unknown deserialization: header binding: $hdrBinding; member: `$memberName`")
            }
        }
    }

    private fun renderDeserializePrefixHeaders(
        ctx: ProtocolGenerator.GenerationContext,
        binding: HttpBinding,
        writer: KotlinWriter
    ) {
        // prefix headers MUST target string or collection-of-string
        val targetShape = ctx.model.expectShape(binding.member.target) as? MapShape
            ?: throw CodegenException("prefixHeader bindings can only be attached to Map shapes")

        val targetValueShape = ctx.model.expectShape(targetShape.value.target)
        val targetValueSymbol = ctx.symbolProvider.toSymbol(targetValueShape)
        val prefix = binding.locationName
        val memberName = binding.member.defaultName()

        val keyCollName = "keysFor${memberName.capitalize()}"
        val filter = if (prefix.isNotEmpty()) ".filter { it.startsWith(\"$prefix\") }" else ""

        writer.write("val $keyCollName = response.headers.names()$filter")
        writer.openBlock("if ($keyCollName.isNotEmpty()) {")
            .write("val map = mutableMapOf<String, ${targetValueSymbol.name}>()")
            .openBlock("for (hdrKey in $keyCollName) {")
            .call {
                val getFn = when (targetValueShape) {
                    is StringShape -> "[hdrKey]"
                    is ListShape -> ".getAll(hdrKey)"
                    is SetShape -> ".getAll(hdrKey)?.toSet()"
                    else -> throw CodegenException("invalid httpPrefixHeaders usage on ${binding.member}")
                }
                // get()/getAll() returns String? or List<String>?, this shouldn't ever trigger the continue though...
                writer.write("val el = response.headers$getFn ?: continue")
                if (prefix.isNotEmpty()) {
                    writer.write("val key = hdrKey.removePrefix(\$S)", prefix)
                    writer.write("map[key] = el")
                } else {
                    writer.write("map[hdrKey] = el")
                }
            }
            .closeBlock("}")
            .write("builder.$memberName = map")
            .closeBlock("}")
    }

    private fun renderExplicitHttpPayloadDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        binding: HttpBinding,
        writer: KotlinWriter
    ) {
        val memberName = binding.member.defaultName()
        val target = ctx.model.expectShape(binding.member.target)
        val targetSymbol = ctx.symbolProvider.toSymbol(target)
        when (target.type) {
            ShapeType.STRING -> {
                writer.write("val contents = response.body.readAll()?.decodeToString()")
                if (target.hasTrait(EnumTrait::class.java)) {
                    writer.addImport(targetSymbol, "")
                    writer.write("builder.$memberName = contents?.let { ${targetSymbol.name}.fromValue(it) }")
                } else {
                    writer.write("builder.$memberName = contents")
                }
            }
            ShapeType.BLOB -> {
                val isBinaryStream = target.hasTrait(StreamingTrait::class.java)
                val conversion = if (isBinaryStream) {
                    "toByteStream()"
                } else {
                    "readAll()"
                }
                writer.write("builder.$memberName = response.body.$conversion")
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                // delegate to the member deserializer
                writer.write("val payload = response.body.readAll()")
                writer.openBlock("if (payload != null) {")
                    .write("val deserializer = provider(payload)")
                    .write("builder.$memberName = \$LDeserializer().deserialize(deserializer)", targetSymbol.name)
                    .closeBlock("}")
            }
            ShapeType.DOCUMENT -> {
                // TODO - implement document support
            }
            else -> throw CodegenException("member shape ${binding.member} deserializer not implemented")
        }

        writer.openBlock("")
            .closeBlock("")
    }

    /**
     * Generate deserializer for all shapes in the set
     */
    private fun generateDocumentDeserializers(ctx: ProtocolGenerator.GenerationContext, shapes: Set<Shape>) {
        for (shape in shapes) {
            val symbol = ctx.symbolProvider.toSymbol(shape)
            // deserializer class for the shape outputs the shape's symbol
            // ensure we get an import statement to the symbol from the .model package
            val reference = SymbolReference.builder()
                .symbol(symbol)
                .options(SymbolReference.ContextOption.DECLARE)
                .build()
            val deserializerSymbol = Symbol.builder()
                .definitionFile("${symbol.name}Deserializer.kt")
                .name("${symbol.name}Deserializer")
                .namespace("${ctx.settings.moduleName}.transform", ".")
                .addReference(reference)
                .build()

            ctx.delegator.useShapeWriter(deserializerSymbol) { writer ->
                renderDocumentDeserializer(ctx, symbol, shape, deserializerSymbol, writer)
            }
        }
    }

    /**
     * Actually renders the deserializer implementation for the given symbol/shape
     * @param ctx The codegen context
     * @param symbol The symbol to generate a deserializer implementation for
     * @param shape The corresponding shape
     * @param deserializerSymbol The deserializer symbol itself being generated
     * @param writer The codegen writer to render to
     */
    private fun renderDocumentDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        symbol: Symbol,
        shape: Shape,
        deserializerSymbol: Symbol,
        writer: KotlinWriter
    ) {
        importSerdePackage(writer)

        writer.write("")
            .openBlock("class \$L {", deserializerSymbol.name)
            .call {
                renderSerdeCompanionObject(shape.members().toList(), writer)
            }
            .call {
                writer.withBlock("fun deserialize(deserializer: Deserializer): ${symbol.name} {", "}") {
                    writer.write("val builder = ${symbol.name}.dslBuilder()")
                    DeserializeStructGenerator(ctx, shape.members().toList(), writer, defaultTimestampFormat).render()
                    writer.write("return builder.build()")
                }
            }
            .closeBlock("}")
    }
}

/**
 * Get the field descriptor name for a member shape
 */
fun MemberShape.descriptorName(): String = "${this.defaultName()}_DESCRIPTOR".toUpperCase()

/**
 * Get the serializer class name for an operation
 */
fun OperationShape.serializerName(): String = StringUtils.capitalize(this.id.name) + "Serializer"

/**
 * Get the deserializer class name for an operation
 */
fun OperationShape.deserializerName(): String = StringUtils.capitalize(this.id.name) + "Deserializer"

fun formatInstant(paramName: String, tsFmt: TimestampFormatTrait.Format, forceString: Boolean = false): String = when (tsFmt) {
    TimestampFormatTrait.Format.EPOCH_SECONDS -> {
        // default to epoch seconds as a double
        if (forceString) {
            "$paramName.format(TimestampFormat.EPOCH_SECONDS)"
        } else {
            "$paramName.toEpochDouble()"
        }
    }
    TimestampFormatTrait.Format.DATE_TIME -> "$paramName.format(TimestampFormat.ISO_8601)"
    TimestampFormatTrait.Format.HTTP_DATE -> "$paramName.format(TimestampFormat.RFC_5322)"
    else -> throw CodegenException("unknown timestamp format: $tsFmt")
}

// import CLIENT-RT.*
internal fun importSerdePackage(writer: KotlinWriter) {
    writer.addImport(KotlinDependency.CLIENT_RT_SERDE.namespace, "*", "")
    writer.dependencies.addAll(KotlinDependency.CLIENT_RT_SERDE.dependencies)
}

// import CLIENT-RT.time.TimestampFormat
internal fun importTimestampFormat(writer: KotlinWriter) {
    writer.addImport("${KotlinDependency.CLIENT_RT_CORE.namespace}.time", "TimestampFormat", "")
}

// import CLIENT-RT.time.Instant
internal fun importInstant(writer: KotlinWriter) {
    writer.addImport("${KotlinDependency.CLIENT_RT_CORE.namespace}.time", "Instant", "")
}

// import CLIENT-RT-UTILS.*
internal fun importBase64Utils(writer: KotlinWriter) {
    // these are extensions on string/bytearray. For now import everything so we don't have to distinguish
    writer.addImport(KotlinDependency.CLIENT_RT_UTILS.namespace, "*", "")
    writer.dependencies.addAll(KotlinDependency.CLIENT_RT_UTILS.dependencies)
}

// return the conversion function to use to convert a Kotlin string to a given number shape
internal fun stringToNumber(shape: NumberShape): String = when (shape.type) {
    ShapeType.BYTE -> "toByte()"
    ShapeType.SHORT -> "toShort()"
    ShapeType.INTEGER -> "toInt()"
    ShapeType.LONG -> "toLong()"
    ShapeType.FLOAT -> "toFloat()"
    ShapeType.DOUBLE -> "toDouble()"
    else -> throw CodegenException("unknown number shape: $shape")
}

// return the conversion function `Instant.fromXYZ(paramName)` for the given format
internal fun parseInstant(paramName: String, tsFmt: TimestampFormatTrait.Format): String = when (tsFmt) {
    TimestampFormatTrait.Format.EPOCH_SECONDS -> "Instant.fromEpochSeconds($paramName)"
    TimestampFormatTrait.Format.DATE_TIME -> "Instant.fromIso8601($paramName)"
    TimestampFormatTrait.Format.HTTP_DATE -> "Instant.fromRfc5322($paramName)"
    else -> throw CodegenException("unknown timestamp format: $tsFmt")
}
