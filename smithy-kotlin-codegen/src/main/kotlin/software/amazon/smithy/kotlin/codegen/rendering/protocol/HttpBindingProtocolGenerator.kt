/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.lang.toEscapedLiteral
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.kotlin.codegen.retries.StandardRetryMiddleware
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*
import java.util.logging.Logger

/**
 * Abstract implementation useful for all HTTP protocols
 */
abstract class HttpBindingProtocolGenerator : ProtocolGenerator {
    private val LOGGER = Logger.getLogger(javaClass.name)

    override val applicationProtocol: ApplicationProtocol = ApplicationProtocol.createDefaultHttpApplicationProtocol()

    /**
     * The default serde format for timestamps.
     */
    abstract val defaultTimestampFormat: TimestampFormatTrait.Format

    /**
     * Returns HTTP binding resolver for protocol specified by input.
     * @param model service model
     * @param serviceShape service under codegen
     */
    abstract fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver

    /**
     * Get the [HttpProtocolClientGenerator] to be used to render the implementation of the service client interface
     */
    abstract fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator

    /**
     * Get all the middleware that should be installed into the operation's middleware stack (`SdkOperationExecution`)
     * This is the function that protocol client generators should invoke to get the fully resolved set of middleware
     * to be rendered (i.e. after integrations have had a chance to intercept). The default set of middleware for
     * a protocol can be overridden by [getDefaultHttpMiddleware].
     */
    fun getHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> {
        val defaultMiddleware = getDefaultHttpMiddleware(ctx)
        return ctx.integrations.fold(defaultMiddleware) { middleware, integration ->
            integration.customizeMiddleware(ctx, middleware)
        }
    }

    /**
     * Template method function that generators can override to return the _default_ set of middleware for the protocol
     */
    protected open fun getDefaultHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> =
        listOf(
            ResolveEndpointMiddleware(),
            StandardRetryMiddleware(),
        )

    /**
     * Return the function responsible for handling an operational error. This function is invoked from the operation
     * deserializer based on [renderIsHttpError] logic (i.e. the response has been determined to be an error, it is
     * up to the function represented by the returned symbol to figure out which one and throw it)
     *
     * The function should have the following signature:
     *
     * ```
     * suspend fun throwFooOperationError(context: ExecutionContext, response: HttpResponse): Nothing {
     *     <-- CURRENT WRITER CONTEXT -->
     * }
     * ```
     *
     * Implementations are expected to throw an exception matched from the response. If none can be matched then throw
     * a suitable generic exception.
     *
     * @param ctx the protocol generator context
     * @param op the operation shape to render error matching
     */
    abstract fun operationErrorHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol

    private fun generateSerializers(ctx: ProtocolGenerator.GenerationContext) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val httpOperations = resolver.bindingOperations()
        // render HttpSerialize for all operation inputs
        httpOperations.forEach { operation ->
            generateOperationSerializer(ctx, operation)
        }
    }

    private fun generateDeserializers(ctx: ProtocolGenerator.GenerationContext) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        // render HttpDeserialize for all operation outputs
        val httpOperations = resolver.bindingOperations()
        httpOperations.forEach { operation ->
            generateOperationDeserializer(ctx, operation)
        }

        // generate HttpDeserialize for exception types
        val modeledErrors = httpOperations.flatMap { it.errors }.map { ctx.model.expectShape(it) as StructureShape }.toSet()
        modeledErrors.forEach { generateExceptionDeserializer(ctx, it) }
    }

    override fun generateProtocolClient(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = ctx.symbolProvider.toSymbol(ctx.service)
        ctx.delegator.useFileWriter("Default${symbol.name}.kt", ctx.settings.pkg.name) { writer ->
            val clientGenerator = getHttpProtocolClientGenerator(ctx)
            clientGenerator.render(writer)
        }
        generateSerializers(ctx)
        generateDeserializers(ctx)
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

        // operation input shapes could be re-used across one or more operations. The protocol details may
        // be different though (e.g. uri/method). We need to generate a serializer/deserializer per/operation
        // NOT per input/output shape
        val serializerSymbol = buildSymbol {
            definitionFile = "${op.serializerName()}.kt"
            name = op.serializerName()
            namespace = "${ctx.settings.pkg.name}.transform"

            reference(inputSymbol, SymbolReference.ContextOption.DECLARE)
        }
        val operationSerializerSymbols = setOf(
            RuntimeTypes.Http.HttpBody,
            RuntimeTypes.Http.HttpMethod,
            RuntimeTypes.Http.Operation.HttpSerialize,
            RuntimeTypes.Http.ByteArrayContent,
            RuntimeTypes.Http.Request.HttpRequestBuilder,
            RuntimeTypes.Http.Request.url
        )
        ctx.delegator.useSymbolWriter(serializerSymbol) { writer ->
            // import all of http, http.request, and serde packages. All serializers requires one or more of the symbols
            // and most require quite a few. Rather than try and figure out which specific ones are used just take them
            // all to ensure all the various DSL builders are available, etc
            writer
                .addImport(operationSerializerSymbols)
                .write("")
                .openBlock("internal class #T: #T<#T> {", serializerSymbol, RuntimeTypes.Http.Operation.HttpSerialize, inputSymbol)
                .call {
                    writer.openBlock("override suspend fun serialize(context: #T, input: #T): #T {", RuntimeTypes.Core.ExecutionContext, inputSymbol, RuntimeTypes.Http.Request.HttpRequestBuilder)
                        .write("val builder = #T()", RuntimeTypes.Http.Request.HttpRequestBuilder)
                        .call {
                            renderHttpSerialize(ctx, op, writer)
                        }
                        .write("return builder")
                        .closeBlock("}")
                }
                .closeBlock("}")
        }
    }

    protected open fun renderHttpSerialize(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val httpTrait = resolver.httpTrait(op)
        val requestBindings = resolver.requestBindings(op)

        writer
            .addImport(RuntimeTypes.Core.ExecutionContext)
            .write("builder.method = #T.#L", RuntimeTypes.Http.HttpMethod, httpTrait.method.uppercase())
            .write("")
            .call {
                // URI components
                writer.withBlock("builder.url {", "}") {
                    renderUri(ctx, op, writer)

                    // Query Parameters
                    renderQueryParameters(ctx, httpTrait, requestBindings, writer)
                }
            }
            .write("")
            .call {
                // headers
                val headerBindings = requestBindings
                    .filter { it.location == HttpBinding.Location.HEADER }
                    .sortedBy { it.memberName }

                val prefixHeaderBindings = requestBindings
                    .filter { it.location == HttpBinding.Location.PREFIX_HEADERS }

                if (headerBindings.isNotEmpty() || prefixHeaderBindings.isNotEmpty()) {
                    writer
                        .addImport(RuntimeTypes.Http.Request.headers)
                        .withBlock("builder.#T {", "}", RuntimeTypes.Http.Request.headers) {
                            renderStringValuesMapParameters(ctx, headerBindings, writer)
                            prefixHeaderBindings.forEach {
                                writer.withBlock("input.${it.member.defaultName()}?.filter { it.value != null }?.forEach { (key, value) ->", "}") {
                                    write("append(\"#L\$key\", value!!)", it.locationName)
                                }
                            }
                        }
                }
            }
            .write("")
            .call {
                renderSerializeHttpBody(ctx, op, writer)
            }
    }

    /**
     * Calls the operation body serializer function and binds the results to `builder.body`.
     * By default if no members are bound to the body this function renders nothing.
     * If there is a payload to render it should be bound to `builder.body` when this function returns
     */
    protected open fun renderSerializeHttpBody(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        if (!resolver.hasHttpBody(op)) return

        // payload member(s)
        val requestBindings = resolver.requestBindings(op)
        val httpPayload = requestBindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
        if (httpPayload != null) {
            renderExplicitHttpPayloadSerializer(ctx, httpPayload, writer)
        } else {
            val documentMembers = requestBindings.filterDocumentBoundMembers()
            // Unbound document members that should be serialized into the document format for the protocol.
            // delegate to the generate operation body serializer function
            val sdg = structuredDataSerializer(ctx)
            val opBodySerializerFn = sdg.operationSerializer(ctx, op, documentMembers)
            writer.write("val payload = #T(context, input)", opBodySerializerFn)
            writer.write("builder.body = #T(payload)", RuntimeTypes.Http.ByteArrayContent)
        }

        resolver.determineRequestContentType(op)?.let { contentType ->
            writer.write("builder.headers.setMissing(\"Content-Type\", #S)", contentType)
        }
    }

    // replace labels with any path bindings
    protected open fun renderUri(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val httpTrait = resolver.httpTrait(op)
        val requestBindings = resolver.requestBindings(op)
        val pathBindings = requestBindings.filter { it.location == HttpBinding.Location.LABEL }

        if (pathBindings.isNotEmpty()) {
            writer.openBlock("val pathSegments = listOf(", ")") {
                httpTrait.uri.segments.forEach { segment ->
                    if (segment.isLabel || segment.isGreedyLabel) {
                        // spec dictates member name and label name MUST be the same
                        val binding = pathBindings.find { binding ->
                            binding.memberName == segment.content
                        } ?: throw CodegenException("failed to find corresponding member for httpLabel `${segment.content}")

                        // shape must be string, number, boolean, or timestamp
                        val targetShape = ctx.model.expectShape(binding.member.target)
                        val identifier = if (targetShape.isTimestampShape) {
                            writer.addImport(RuntimeTypes.Core.TimestampFormat)
                            val tsFormat = resolver.determineTimestampFormat(
                                binding.member,
                                HttpBinding.Location.LABEL,
                                defaultTimestampFormat
                            )
                            val tsLabel = formatInstant("input.${binding.member.defaultName()}?", tsFormat, forceString = true)
                            tsLabel
                        } else {
                            "input.${binding.member.defaultName()}"
                        }

                        val encodeSymbol = RuntimeTypes.Http.encodeLabel
                        writer.addImport(encodeSymbol)
                        val encodeFn = if (segment.isGreedyLabel) {
                            writer.format("#T(greedy = true)", encodeSymbol)
                        } else {
                            writer.format("#T()", encodeSymbol)
                        }
                        writer.write("#S.$encodeFn,", "\${$identifier}")
                    } else {
                        // literal
                        writer.write("\"#L\",", segment.content.toEscapedLiteral())
                    }
                }
            }

            writer.write("""path = pathSegments.joinToString(separator = "/", prefix = "/")""")
        } else {
            // all literals, inline directly
            val resolvedPath = httpTrait.uri.segments.joinToString(
                separator = "/",
                prefix = "/",
                transform = {
                    it.content.toEscapedLiteral()
                }
            )
            writer.write("path = \"#L\"", resolvedPath)
        }
    }

    private fun renderQueryParameters(
        ctx: ProtocolGenerator.GenerationContext,
        httpTrait: HttpTrait,
        requestBindings: List<HttpBindingDescriptor>,
        writer: KotlinWriter
    ) {

        // literals in the URI
        val queryLiterals = httpTrait.uri.queryLiterals

        // shape bindings
        val queryBindings = requestBindings.filter { it.location == HttpBinding.Location.QUERY }

        // maps bound via httpQueryParams trait
        val queryMapBindings = requestBindings.filter { it.location == HttpBinding.Location.QUERY_PARAMS }

        if (queryBindings.isEmpty() && queryLiterals.isEmpty() && queryMapBindings.isEmpty()) return

        writer
            .addImport(RuntimeTypes.Http.parameters)
            .withBlock("#T {", "}", RuntimeTypes.Http.parameters) {
                queryLiterals.forEach { (key, value) ->
                    writer.write("append(#S, #S)", key, value)
                }

                renderStringValuesMapParameters(ctx, queryBindings, writer)

                queryMapBindings.forEach {
                    // either Map<String, String> or Map<String, Collection<String>>
                    // https://awslabs.github.io/smithy/1.0/spec/core/http-traits.html#httpqueryparams-trait
                    val target = ctx.model.expectShape<MapShape>(it.member.target)
                    val valueTarget = ctx.model.expectShape(target.value.target)
                    val fn = when (valueTarget.type) {
                        ShapeType.STRING -> "append"
                        ShapeType.LIST, ShapeType.SET -> "appendAll"
                        else -> throw CodegenException("unexpected value type for httpQueryParams map")
                    }

                    val nullCheck = if (target.hasTrait<SparseTrait>()) {
                        "if (value != null) "
                    } else {
                        ""
                    }

                    writer.write("input.${it.member.defaultName()}")
                        .indent()
                        // ensure query precedence rules are enforced by filtering keys already set
                        // (httpQuery bound members take precedence over a query map with same key)
                        .write("?.filterNot{ contains(it.key) }")
                        .withBlock("?.forEach { (key, value) ->", "}") {
                            write("${nullCheck}$fn(key, value)")
                        }
                        .dedent()
                }
            }
    }

    // shared implementation for rendering members that belong to StringValuesMap (e.g. Header or Query parameters)
    private fun renderStringValuesMapParameters(
        ctx: ProtocolGenerator.GenerationContext,
        bindings: List<HttpBindingDescriptor>,
        writer: KotlinWriter
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        HttpStringValuesMapSerializer(ctx, bindings, resolver, defaultTimestampFormat).render(writer)
    }

    /**
     * Render serialization for a member bound with the `httpPayload` trait
     *
     * @param ctx The code generation context
     * @param op The operation shape
     * @param binding The explicit payload binding
     * @param writer The code writer to render to
     */
    private fun renderExplicitHttpPayloadSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        binding: HttpBindingDescriptor,
        writer: KotlinWriter
    ) {
        // explicit payload member as the sole payload
        val memberName = binding.member.defaultName()
        writer.openBlock("if (input.#L != null) {", memberName)

        val target = ctx.model.expectShape(binding.member.target)

        when (target.type) {
            ShapeType.BLOB -> {
                val isBinaryStream = ctx.model.expectShape(binding.member.target).hasTrait<StreamingTrait>()
                if (isBinaryStream) {
                    writer.write("builder.body = input.#L.#T() ?: #T.Empty", memberName, RuntimeTypes.Http.toHttpBody, RuntimeTypes.Http.HttpBody)
                } else {
                    writer.write("builder.body = #T(input.#L)", RuntimeTypes.Http.ByteArrayContent, memberName)
                }
            }
            ShapeType.STRING -> {
                val contents = if (target.isEnum) {
                    "$memberName.value"
                } else {
                    memberName
                }
                writer.write("builder.body = #T(input.#L.#T())", RuntimeTypes.Http.ByteArrayContent, contents, KotlinTypes.Text.encodeToByteArray)
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                val sdg = structuredDataSerializer(ctx)
                val payloadSerializerFn = sdg.payloadSerializer(ctx, binding.member)
                writer.write("val payload = #T(input.#L)", payloadSerializerFn, memberName)
                writer.write("builder.body = #T(payload)", RuntimeTypes.Http.ByteArrayContent)
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

        // operation output shapes could be re-used across one or more operations. The protocol details may
        // be different though (e.g. uri/method). We need to generate a serializer/deserializer per/operation
        // NOT per input/output shape
        val deserializerSymbol = buildSymbol {
            val definitionFileName = op.deserializerName().replaceFirstChar(Char::uppercaseChar)
            definitionFile = "$definitionFileName.kt"
            name = op.deserializerName()
            namespace = ctx.settings.pkg.subpackage("transform")

            reference(outputSymbol, SymbolReference.ContextOption.DECLARE)
        }
        val operationDeserializerSymbols = setOf(
            RuntimeTypes.Http.Operation.HttpDeserialize,
            RuntimeTypes.Http.Response.HttpResponse,
        )

        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val responseBindings = resolver.responseBindings(op)
        ctx.delegator.useSymbolWriter(deserializerSymbol) { writer ->
            // import all of http, http.response , and serde packages. All serializers requires one or more of the symbols
            // and most require quite a few. Rather than try and figure out which specific ones are used just take them
            // all to ensure all the various DSL builders are available, etc
            writer
                .addImport(operationDeserializerSymbols)
                .write("")
                .openBlock(
                    "internal class #T: #T<#T> {",
                    deserializerSymbol,
                    RuntimeTypes.Http.Operation.HttpDeserialize,
                    outputSymbol
                )
                .write("")
                .call {
                    renderHttpDeserialize(ctx, outputSymbol, responseBindings, op, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Renders the logic to detect if an HTTP response should be considered an error for this operation
     */
    protected open fun renderIsHttpError(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.addImport(RuntimeTypes.Http.isSuccess)
        writer.withBlock("if (!response.status.#T()) {", "}", RuntimeTypes.Http.isSuccess) {
            val errorHandlerFn = operationErrorHandler(ctx, op)
            write("#T(context, response)", errorHandlerFn)
        }
    }

    /**
     * Generate HttpDeserialize for a modeled error (exception)
     */
    private fun generateExceptionDeserializer(ctx: ProtocolGenerator.GenerationContext, shape: StructureShape) {
        val outputSymbol = ctx.symbolProvider.toSymbol(shape)
        val exceptionDeserializerSymbols = setOf(
            RuntimeTypes.Core.ExecutionContext,
            RuntimeTypes.Http.Response.HttpResponse,
            RuntimeTypes.Serde.SdkObjectDescriptor,
            RuntimeTypes.Serde.SdkFieldDescriptor,
            RuntimeTypes.Serde.SerialKind,
            RuntimeTypes.Serde.deserializeStruct,
            RuntimeTypes.Http.Response.HttpResponse,
            RuntimeTypes.Http.Operation.HttpDeserialize,
        )

        val deserializerSymbol = buildSymbol {
            val deserializerName = "${outputSymbol.name}Deserializer"
            definitionFile = "$deserializerName.kt"
            name = deserializerName
            namespace = ctx.settings.pkg.subpackage("transform")
            reference(outputSymbol, SymbolReference.ContextOption.DECLARE)
        }

        ctx.delegator.useSymbolWriter(deserializerSymbol) { writer ->
            val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
            val responseBindings = resolver.responseBindings(shape)
            writer
                .addImport(exceptionDeserializerSymbols)
                .write("")
                .openBlock("internal class #T: #T<#T> {", deserializerSymbol, RuntimeTypes.Http.Operation.HttpDeserialize, outputSymbol)
                .write("")
                .call {
                    renderHttpDeserialize(ctx, outputSymbol, responseBindings, null, writer)
                }
                .closeBlock("}")
        }
    }

    private fun renderHttpDeserialize(
        ctx: ProtocolGenerator.GenerationContext,
        outputSymbol: Symbol,
        responseBindings: List<HttpBindingDescriptor>,
        // this method is shared between operation and exception deserialization. In the case of operations this MUST be set
        op: OperationShape?,
        writer: KotlinWriter
    ) {
        writer
            .addImport(RuntimeTypes.Core.ExecutionContext)
            .openBlock(
                "override suspend fun deserialize(context: #T, response: #T): #T {",
                RuntimeTypes.Core.ExecutionContext,
                RuntimeTypes.Http.Response.HttpResponse,
                outputSymbol
            )
            .call {
                if (outputSymbol.shape?.isError == false && op != null) {
                    // handle operation errors
                    renderIsHttpError(ctx, op, writer)
                }
            }
            .write("val builder = #T.Builder()", outputSymbol)
            .write("")
            .call {
                // headers
                val headerBindings = responseBindings
                    .filter { it.location == HttpBinding.Location.HEADER }
                    .sortedBy { it.memberName }

                renderDeserializeHeaders(ctx, headerBindings, writer)

                // prefix headers
                // spec: "Only a single structure member can be bound to httpPrefixHeaders"
                responseBindings.firstOrNull { it.location == HttpBinding.Location.PREFIX_HEADERS }
                    ?.let {
                        renderDeserializePrefixHeaders(ctx, it, writer)
                    }
            }
            .write("")
            .call {
                // document members
                // payload member(s)
                val httpPayload = responseBindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
                if (httpPayload != null) {
                    renderExplicitHttpPayloadDeserializer(ctx, httpPayload, writer)
                } else {
                    // Unbound document members that should be deserialized from the document format for the protocol.
                    val documentMembers = responseBindings
                        .filter { it.location == HttpBinding.Location.DOCUMENT }
                        .sortedBy { it.memberName }
                        .map { it.member }

                    if (documentMembers.isNotEmpty()) {
                        val sdg = structuredDataParser(ctx)

                        val bodyDeserializerFn = if (op != null) {
                            // normal operation
                            sdg.operationDeserializer(ctx, op, documentMembers)
                        } else {
                            // error
                            sdg.errorDeserializer(ctx, outputSymbol.shape as StructureShape, documentMembers)
                        }

                        writer.write("val payload = response.body.#T()", RuntimeTypes.Http.readAll)
                            .withBlock("if (payload != null) {", "}") {
                                write("#T(builder, payload)", bodyDeserializerFn)
                            }
                    }
                }
            }
            .call {
                responseBindings.firstOrNull { it.location == HttpBinding.Location.RESPONSE_CODE }
                    ?.let {
                        renderDeserializeResponseCode(ctx, it, writer)
                    }
            }
            .write("return builder.build()")
            .closeBlock("}")
    }

    /**
     * Render mapping http response code value to response type.
     */
    private fun renderDeserializeResponseCode(ctx: ProtocolGenerator.GenerationContext, binding: HttpBindingDescriptor, writer: KotlinWriter) {
        val memberName = binding.member.defaultName()
        val memberTarget = ctx.model.expectShape(binding.member.target)

        check(memberTarget.type == ShapeType.INTEGER) { "Unexpected target type in response code deserialization: ${memberTarget.id} (${memberTarget.type})" }

        writer.write("builder.#L = response.status.value", memberName)
    }

    /**
     * Render deserialization of all members bound to a response header
     */
    private fun renderDeserializeHeaders(
        ctx: ProtocolGenerator.GenerationContext,
        bindings: List<HttpBindingDescriptor>,
        writer: KotlinWriter
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        bindings.forEach { hdrBinding ->
            val memberTarget = ctx.model.expectShape(hdrBinding.member.target)
            val memberName = hdrBinding.member.defaultName()
            val headerName = hdrBinding.locationName

            val targetSymbol = ctx.symbolProvider.toSymbol(hdrBinding.member)
            val defaultValuePostfix = if (targetSymbol.isNotBoxed && targetSymbol.defaultValue() != null) {
                " ?: ${targetSymbol.defaultValue()}"
            } else {
                ""
            }

            when (memberTarget) {
                is NumberShape -> {
                    writer.write(
                        "builder.#L = response.headers[#S]?.#L$defaultValuePostfix",
                        memberName, headerName, stringToNumber(memberTarget)
                    )
                }
                is BooleanShape -> {
                    writer.write(
                        "builder.#L = response.headers[#S]?.toBoolean()$defaultValuePostfix",
                        memberName, headerName
                    )
                }
                is BlobShape -> {
                    writer
                        .addImport("decodeBase64", KotlinDependency.UTILS)
                        .write("builder.#L = response.headers[#S]?.decodeBase64()", memberName, headerName)
                }
                is StringShape -> {
                    when {
                        memberTarget.isEnum -> {
                            val enumSymbol = ctx.symbolProvider.toSymbol(memberTarget)
                            writer.addImport(enumSymbol)
                            writer.write(
                                "builder.#L = response.headers[#S]?.let { #T.fromValue(it) }",
                                memberName,
                                headerName,
                                enumSymbol
                            )
                        }
                        memberTarget.hasTrait<MediaTypeTrait>() -> {
                            writer
                                .addImport("decodeBase64", KotlinDependency.UTILS)
                                .write("builder.#L = response.headers[#S]?.decodeBase64()", memberName, headerName)
                        }
                        else -> {
                            writer.write("builder.#L = response.headers[#S]", memberName, headerName)
                        }
                    }
                }
                is TimestampShape -> {
                    val tsFormat = resolver.determineTimestampFormat(
                        hdrBinding.member,
                        HttpBinding.Location.HEADER,
                        defaultTimestampFormat
                    )
                    writer
                        .addImport(RuntimeTypes.Core.Instant)
                        .write(
                            "builder.#L = response.headers[#S]?.let { #L }",
                            memberName, headerName, parseInstant("it", tsFormat)
                        )
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
                            val tsFormat = resolver.determineTimestampFormat(
                                hdrBinding.member,
                                HttpBinding.Location.HEADER,
                                defaultTimestampFormat
                            )
                            if (tsFormat == TimestampFormatTrait.Format.HTTP_DATE) {
                                splitFn = "splitHttpDateHeaderListValues"
                            }
                            writer.addImport(RuntimeTypes.Core.Instant)
                            parseInstant("it", tsFormat)
                        }
                        is StringShape -> {
                            when {
                                collectionMemberTarget.isEnum -> {
                                    val enumSymbol = ctx.symbolProvider.toSymbol(collectionMemberTarget)
                                    writer.addImport(enumSymbol)
                                    "${enumSymbol.name}.fromValue(it)"
                                }
                                collectionMemberTarget.hasTrait<MediaTypeTrait>() -> {
                                    writer.addImport("decodeBase64", KotlinDependency.UTILS)
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

                    val mapFn = if (conversion.isNotEmpty()) {
                        "?.map { $conversion }"
                    } else {
                        ""
                    }

                    // writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.util", splitFn)
                    writer
                        .addImport(splitFn, KotlinDependency.HTTP, subpackage = "util")
                        .write("builder.#L = response.headers.getAll(#S)?.flatMap(::$splitFn)${mapFn}$toCollectionType", memberName, headerName)
                }
                else -> throw CodegenException("unknown deserialization: header binding: $hdrBinding; member: `$memberName`")
            }
        }
    }

    private fun renderDeserializePrefixHeaders(
        ctx: ProtocolGenerator.GenerationContext,
        binding: HttpBindingDescriptor,
        writer: KotlinWriter
    ) {
        // prefix headers MUST target string or collection-of-string
        val targetShape = ctx.model.expectShape(binding.member.target) as? MapShape
            ?: throw CodegenException("prefixHeader bindings can only be attached to Map shapes")

        val targetValueShape = ctx.model.expectShape(targetShape.value.target)
        val targetValueSymbol = ctx.symbolProvider.toSymbol(targetValueShape)
        val prefix = binding.locationName
        val memberName = binding.member.defaultName()

        val keyMemberName = memberName.replaceFirstChar { c -> c.uppercaseChar() }
        val keyCollName = "keysFor$keyMemberName"
        val filter = if (prefix?.isNotEmpty() == true) {
            ".filter { it.startsWith(\"$prefix\") }"
        } else {
            ""
        }

        writer.write("val $keyCollName = response.headers.names()$filter")
        writer.openBlock("if ($keyCollName.isNotEmpty()) {")
            .write("val map = mutableMapOf<String, #T>()", targetValueSymbol)
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
                if (prefix?.isNotEmpty() == true) {
                    writer.write("val key = hdrKey.removePrefix(#S)", prefix)
                    writer.write("map[key] = el")
                } else {
                    writer.write("map[hdrKey] = el")
                }
            }
            .closeBlock("}")
            .write("builder.$memberName = map")
            .closeBlock("} else {")
            .indent()
            .write("builder.$memberName = emptyMap()")
            .dedent()
            .write("}")
    }

    private fun renderExplicitHttpPayloadDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        binding: HttpBindingDescriptor,
        writer: KotlinWriter
    ) {
        val memberName = binding.member.defaultName()
        val target = ctx.model.expectShape(binding.member.target)
        val targetSymbol = ctx.symbolProvider.toSymbol(target)
        when (target.type) {
            ShapeType.STRING -> {
                writer
                    .addImport(RuntimeTypes.Http.readAll)
                    .write("val contents = response.body.#T()?.decodeToString()", RuntimeTypes.Http.readAll)
                if (target.isEnum) {
                    writer.addImport(targetSymbol)
                    writer.write("builder.$memberName = contents?.let { #T.fromValue(it) }", targetSymbol)
                } else {
                    writer.write("builder.$memberName = contents")
                }
            }
            ShapeType.BLOB -> {
                val isBinaryStream = target.hasTrait<StreamingTrait>()
                val conversion = if (isBinaryStream) {
                    writer.addImport(RuntimeTypes.Http.toByteStream)
                    "toByteStream()"
                } else {
                    writer.addImport(RuntimeTypes.Http.readAll)
                    "readAll()"
                }
                writer.write("builder.$memberName = response.body.$conversion")
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                // delegate to the payload deserializer
                val sdg = structuredDataParser(ctx)
                val payloadDeserializerFn = sdg.payloadDeserializer(ctx, binding.member)

                writer.write("val payload = response.body.#T()", RuntimeTypes.Http.readAll)
                    .withBlock("if (payload != null) {", "}") {
                        write("builder.$memberName = #T(payload)", payloadDeserializerFn)
                    }
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
     * Determine if the given request/response bindings require a function to be generated to serialize/deserialize
     * to/from the payload
     */
    private fun requiresBodySerde(
        ctx: ProtocolGenerator.GenerationContext,
        bindings: List<HttpBindingDescriptor>
    ): Boolean {
        val httpPayload = bindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }

        return if (httpPayload != null) {
            // only render a serialize/deserialize operation body fn when
            // the explicitly bound payload type requires it
            val target = ctx.model.expectShape(httpPayload.member.target)
            when (target.type) {
                ShapeType.STRUCTURE, ShapeType.UNION -> true
                else -> false
            }
        } else {
            // test if the request/response bindings have any members bound to the HTTP payload (body)
            bindings.any { it.location == HttpBinding.Location.PAYLOAD || it.location == HttpBinding.Location.DOCUMENT }
        }
    }
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

/**
 * Return member shapes bound to the DOCUMENT
 */
fun List<HttpBindingDescriptor>.filterDocumentBoundMembers(): List<MemberShape> =
    filter { it.location == HttpBinding.Location.DOCUMENT }
        .sortedBy { it.memberName }
        .map { it.member }

/**
 * The default operation error handler function name
 */
fun OperationShape.errorHandlerName(): String = "throw${capitalizedDefaultName()}Error"

/**
 * Get the function responsible for handling errors for this operation
 */
fun OperationShape.errorHandler(settings: KotlinSettings, block: SymbolRenderer): Symbol = buildSymbol {
    name = errorHandlerName()
    namespace = "${settings.pkg.name}.transform"
    // place error handler in same file as operation deserializer
    definitionFile = "${deserializerName()}.kt"
    renderBy = block
}
