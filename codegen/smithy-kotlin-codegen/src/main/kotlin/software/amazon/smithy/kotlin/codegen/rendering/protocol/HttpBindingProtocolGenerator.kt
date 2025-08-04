/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
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
import software.amazon.smithy.kotlin.codegen.rendering.serde.deserializerName
import software.amazon.smithy.kotlin.codegen.rendering.serde.formatInstant
import software.amazon.smithy.kotlin.codegen.rendering.serde.parseInstantExpr
import software.amazon.smithy.kotlin.codegen.rendering.serde.serializerName
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*
import java.util.logging.Logger

/**
 * Abstract implementation useful for all HTTP protocols
 */
abstract class HttpBindingProtocolGenerator : ProtocolGenerator {
    private val logger = Logger.getLogger(javaClass.name)

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
    open fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator {
        val middleware = getHttpMiddleware(ctx)
        return HttpProtocolClientGenerator(ctx, middleware, getProtocolHttpBindingResolver(ctx.model, ctx.service))
    }

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
    protected open fun getDefaultHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> = emptyList()

    /**
     * Return the function responsible for handling an operational error. This function is invoked from the operation
     * deserializer based on [renderIsHttpError] logic (i.e. the response has been determined to be an error, it is
     * up to the function represented by the returned symbol to figure out which one and throw it)
     *
     * The function should have the following signature:
     *
     * ```
     * fun throwFooOperationError(context: ExecutionContext, call: HttpCall, payload: ByteArray?): Nothing {
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

    // FIXME - probably make abstract and let individual protocols throw if they don't support event stream bindings

    /**
     * ```
     * private suspend fun serializeOperationFooEventStream(input: Foo): HttpBody
     * ```
     *
     * @param ctx the protocol generator context
     * @param op the operation shape to return event stream serializer for
     */
    open fun eventStreamRequestHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol = error("event streams are not supported by $this")

    /**
     *
     * ```
     * private suspend fun deserializeOperationFooEventStream(builder: Foo.Builder, body: HttpBody)
     * ```
     *
     * @param ctx the protocol generator context
     * @param op the operation shape to return event stream deserializer for
     */
    open fun eventStreamResponseHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol = error("event streams are not supported by $this")

    private fun generateSerializers(ctx: ProtocolGenerator.GenerationContext) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val httpOperations = resolver.bindingOperations()
        // render HttpSerialize for all operation inputs
        httpOperations.forEach { operation ->
            generateOperationSerializer(ctx, operation)
        }

        if (ctx.settings.build.generateServiceProject) {
            val modeledErrors = httpOperations.flatMap { it.errors }.map { ctx.model.expectShape(it) as StructureShape }.toSet()
            modeledErrors.forEach { generateExceptionSerializer(ctx, it) }
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
        if (!ctx.settings.build.generateServiceProject) {
            val modeledErrors = httpOperations.flatMap { it.errors }.map { ctx.model.expectShape(it) as StructureShape }.toSet()
            modeledErrors.forEach { generateExceptionDeserializer(ctx, it) }
        }
    }

    override fun generateProtocolClient(ctx: ProtocolGenerator.GenerationContext) {
        if (ctx.settings.build.generateServiceProject) {
            require(protocolName in listOf("smithyRpcv2cbor", "awsRestjson1")) { "service project accepts only Cbor or JSON protocol" }
        }
        if (!ctx.settings.build.generateServiceProject) {
            val symbol = ctx.symbolProvider.toSymbol(ctx.service)
            ctx.delegator.useFileWriter("Default${symbol.name}.kt", ctx.settings.pkg.name) { writer ->
                val clientGenerator = getHttpProtocolClientGenerator(ctx)
                clientGenerator.render(writer)
            }
        }
        generateSerializers(ctx)
        generateDeserializers(ctx)
    }

    /**
     * Generate request serializer (HttpSerialize) for an operation
     */
    private fun generateOperationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape) {
        val serializationTarget = if (ctx.settings.build.generateServiceProject) {
            op.output
        } else {
            op.input
        }
        if (!serializationTarget.isPresent) {
            return
        }

        val serializationShape = ctx.model.expectShape(serializationTarget.get())
        val serializationSymbol = ctx.symbolProvider.toSymbol(serializationShape)

        // operation input shapes could be re-used across one or more operations. The protocol details may
        // be different though (e.g. uri/method). We need to generate a serializer/deserializer per/operation
        // NOT per input/output shape
        val serializerSymbol = buildSymbol {
            definitionFile = "${op.serializerName()}.kt"
            name = op.serializerName()
            namespace = ctx.settings.pkg.serde

            reference(serializationSymbol, SymbolReference.ContextOption.DECLARE)
        }
        val operationSerializerSymbols = setOf(
            RuntimeTypes.Http.HttpBody,
            RuntimeTypes.Http.HttpMethod,
            RuntimeTypes.Http.Request.url,
        )

        val serdeMeta = HttpSerdeMeta(op.isInputEventStream(ctx.model))

        ctx.delegator.useSymbolWriter(serializerSymbol) { writer ->
            if (ctx.settings.build.generateServiceProject) {
                val serializerResultSymbol = when (protocolName) {
                    "smithyRpcv2cbor" -> KotlinTypes.ByteArray
                    "awsRestjson1" -> KotlinTypes.String
                    else -> KotlinTypes.ByteArray
                }

                val defaultResponse = when (protocolName) {
                    "smithyRpcv2cbor" -> "ByteArray(0)"
                    "awsRestjson1" -> "\"\""
                    else -> "ByteArray(0)"
                }
                writer
                    .openBlock("internal class #T {", serializerSymbol)
                    .call {
                        writer.openBlock(
                            "public fun serialize(context: #T, input: #T): #T {",
                            RuntimeTypes.Core.ExecutionContext,
                            serializationSymbol,
                            serializerResultSymbol,
                        )
                            .write("var response: #T = $defaultResponse", serializerResultSymbol)
                            .call {
                                renderSerializeHttpBody(ctx, op, writer)
                            }
                            .write("return response")
                            .closeBlock("}")
                    }
                    .closeBlock("}")
            } else {
                writer
                    .addImport(operationSerializerSymbols)
                    .write("")
                    .openBlock("internal class #T: #T.#L<#T> {", serializerSymbol, RuntimeTypes.HttpClient.Operation.HttpSerializer, serdeMeta.variantName, serializationSymbol)
                    .call {
                        val modifier = if (serdeMeta.isStreaming) "suspend " else ""
                        writer.openBlock(
                            "override #Lfun serialize(context: #T, input: #T): #T {",
                            modifier,
                            RuntimeTypes.Core.ExecutionContext,
                            serializationSymbol,
                            RuntimeTypes.Http.Request.HttpRequestBuilder,
                        )
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
    }

    protected open fun renderHttpSerialize(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val httpTrait = resolver.httpTrait(op)
        val bindings = resolver.requestBindings(op)

        writer
            .addImport(RuntimeTypes.Core.ExecutionContext)
            .write("builder.method = #T.#L", RuntimeTypes.Http.HttpMethod, httpTrait.method.uppercase())
            .write("")
            .call {
                // URI components
                writer.withBlock("builder.url {", "}") {
                    renderUri(ctx, op, writer)

                    // Query Parameters
                    renderQueryParameters(ctx, httpTrait, bindings, writer)
                }
            }
            .write("")
            .call {
                // headers
                val headerBindings = bindings
                    .filter { it.location == HttpBinding.Location.HEADER }
                    .sortedBy { it.memberName }

                val prefixHeaderBindings = bindings
                    .filter { it.location == HttpBinding.Location.PREFIX_HEADERS }

                if (headerBindings.isNotEmpty() || prefixHeaderBindings.isNotEmpty()) {
                    writer
                        .addImport(RuntimeTypes.Http.Request.headers)
                        .withBlock("builder.#T {", "}", RuntimeTypes.Http.Request.headers) {
                            renderStringValuesMapParameters(ctx, headerBindings, writer)
                            prefixHeaderBindings.forEach {
                                writer.withBlock("input.${it.member.defaultName()}?.forEach { (key, value) ->", "}") {
                                    write("append(\"#L\$key\", value)", it.locationName)
                                }
                            }
                        }
                }
            }
            .write("")
            .call {
                if (op.isInputEventStream(ctx.model)) {
                    val eventStreamSerializeFn = eventStreamRequestHandler(ctx, op)
                    writer.write("builder.body = #T(context, input)", eventStreamSerializeFn)
                    renderContentTypeHeader(ctx, op, writer, resolver)
                } else {
                    renderSerializeHttpBody(ctx, op, writer)
                }
            }
    }

    /**
     * Generate HttpSerialize for a modeled error (exception)
     */
    private fun generateExceptionSerializer(ctx: ProtocolGenerator.GenerationContext, shape: StructureShape) {
        val serializationSymbol = ctx.symbolProvider.toSymbol(shape)

        val serializerSymbol = buildSymbol {
            val deserializerName = "${serializationSymbol.name}Serializer"
            definitionFile = "$deserializerName.kt"
            name = deserializerName
            namespace = ctx.settings.pkg.serde
            reference(serializationSymbol, SymbolReference.ContextOption.DECLARE)
        }

        ctx.delegator.useSymbolWriter(serializerSymbol) { writer ->
            val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
            val bindings = resolver.responseBindings(shape)
            val serializerResultSymbol = when (protocolName) {
                "smithyRpcv2cbor" -> KotlinTypes.ByteArray
                "awsRestjson1" -> KotlinTypes.String
                else -> KotlinTypes.ByteArray
            }

            val defaultResponse = when (protocolName) {
                "smithyRpcv2cbor" -> "ByteArray(0)"
                "awsRestjson1" -> "\"\""
                else -> "ByteArray(0)"
            }
            writer.withBlock("internal class #T {", "}", serializerSymbol) {
                writer.openBlock(
                    "public fun serialize(context: #T, input: #T): #T {",
                    RuntimeTypes.Core.ExecutionContext,
                    serializationSymbol,
                    serializerResultSymbol,
                )
                    .write("var response: #T = $defaultResponse", serializerResultSymbol)
                    .call {
                        renderExceptionSerializeBody(ctx, serializationSymbol, bindings, writer)
                    }
                    .write("return response")
                    .closeBlock("}")
            }
        }
    }

    /**
     * Calls the operation body serializer function and binds the results to `builder.body`.
     * By default if no members are bound to the body this function renders nothing.
     * If there is a payload to render it should be bound to `builder.body` when this function returns
     */
    protected open fun renderSerializeHttpBody(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        if (ctx.settings.build.generateServiceProject) {
            if (!resolver.hasHttpResponseBody(op)) return
        } else {
            if (!resolver.hasHttpRequestBody(op)) return
        }

        // payload member(s)
        val bindings = if (ctx.settings.build.generateServiceProject) {
            resolver.responseBindings(op)
        } else {
            resolver.requestBindings(op)
        }
        val httpPayload = bindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
        if (httpPayload != null) {
            renderExplicitHttpPayloadSerializer(ctx, httpPayload, writer)
        } else {
            val documentMembers = bindings.filterDocumentBoundMembers()
            // Unbound document members that should be serialized into the document format for the protocol.
            // delegate to the generate operation body serializer function
            val sdg = structuredDataSerializer(ctx)
            val opBodySerializerFn = sdg.operationSerializer(ctx, op, documentMembers)
            writer.write("val payload = #T(context, input)", opBodySerializerFn)
            if (ctx.settings.build.generateServiceProject) {
                writer.write("response = payload.decodeToString()")
            } else {
                writer.write("builder.body = #T.fromBytes(payload)", RuntimeTypes.Http.HttpBody)
            }
        }
        if (!ctx.settings.build.generateServiceProject) {
            renderContentTypeHeader(ctx, op, writer, resolver)
        }
    }

    /**
     * Calls the operation body serializer function and binds the results to `builder.body`.
     * By default if no members are bound to the body this function renders nothing.
     * If there is a payload to render it should be bound to `builder.body` when this function returns
     */
    protected open fun renderExceptionSerializeBody(
        ctx: ProtocolGenerator.GenerationContext,
        deserializationSymbol: Symbol,
        bindings: List<HttpBindingDescriptor>,
        writer: KotlinWriter,
    ) {
        val documentMembers = bindings.filterDocumentBoundMembers()
        // Unbound document members that should be serialized into the document format for the protocol.
        // delegate to the generate operation body serializer function
        val sdg = structuredDataSerializer(ctx)
        val exceptionBodySerializerFn = sdg.errorSerializer(ctx, deserializationSymbol.shape as StructureShape, documentMembers)
        writer.write("val payload = #T(context, input)", exceptionBodySerializerFn)
        writer.write("response = payload")
    }

    protected open fun renderContentTypeHeader(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
        resolver: HttpBindingResolver = getProtocolHttpBindingResolver(ctx.model, ctx.service),
    ) {
        resolver.determineRequestContentType(op)?.let { contentType ->
            writer.withBlock("if (builder.body !is HttpBody.Empty) {", "}") {
                write("builder.headers.setMissing(\"Content-Type\", #S)", contentType)
            }
        }
    }

    // replace labels with any path bindings
    protected open fun renderUri(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val httpTrait = resolver.httpTrait(op)
        val bindings = if (ctx.settings.build.generateServiceProject) {
            resolver.responseBindings(op)
        } else {
            resolver.requestBindings(op)
        }
        val pathBindings = bindings.filter { it.location == HttpBinding.Location.LABEL }

        if (pathBindings.isNotEmpty()) {
            // One of the few places we generate client side validation
            // httpLabel bindings must be non-null
            httpTrait.uri.segments.filter { it.isLabel || it.isGreedyLabel }.forEach { segment ->
                val bindings = pathBindings.find {
                    it.memberName == segment.content
                } ?: throw CodegenException("failed to find corresponding member for httpLabel `${segment.content}`")

                val memberSymbol = ctx.symbolProvider.toSymbol(bindings.member)
                if (memberSymbol.isNullable) {
                    writer.write("""requireNotNull(input.#1L) { "#1L is bound to the URI and must not be null" }""", bindings.member.defaultName())
                }

                // check length trait if applicable
                renderNonBlankGuard(ctx, bindings.member, writer)
            }

            if (httpTrait.uri.segments.isNotEmpty()) {
                writer.withBlock("path.encodedSegments {", "}") {
                    httpTrait.uri.segments.forEach { segment ->
                        if (segment.isLabel || segment.isGreedyLabel) {
                            // spec dictates member name and label name MUST be the same
                            val bindings = pathBindings.find { binding ->
                                binding.memberName == segment.content
                            }
                                ?: throw CodegenException("failed to find corresponding member for httpLabel `${segment.content}")

                            // shape must be string, number, boolean, or timestamp
                            val targetShape = ctx.model.expectShape(bindings.member.target)
                            val memberSymbol = ctx.symbolProvider.toSymbol(bindings.member)
                            val identifier = when {
                                targetShape.isTimestampShape -> {
                                    addImport(RuntimeTypes.Core.TimestampFormat)
                                    val tsFormat = resolver.determineTimestampFormat(
                                        bindings.member,
                                        HttpBinding.Location.LABEL,
                                        defaultTimestampFormat,
                                    )
                                    val nullCheck = if (memberSymbol.isNullable) "?" else ""
                                    val tsLabel = formatInstant(
                                        "input.${bindings.member.defaultName()}$nullCheck",
                                        tsFormat,
                                        forceString = true,
                                    )
                                    tsLabel
                                }

                                targetShape.isStringEnumShape -> "input.${bindings.member.defaultName()}.value"
                                targetShape.isIntEnumShape -> "input.${bindings.member.defaultName()}.value.toString()"

                                targetShape.isStringShape -> "input.${bindings.member.defaultName()}"

                                else -> "input.${bindings.member.defaultName()}.toString()"
                            }

                            val encodeFn =
                                format("#T.SmithyLabel.encode", RuntimeTypes.Core.Text.Encoding.PercentEncoding)

                            if (segment.isGreedyLabel) {
                                write("#L.split(#S).mapTo(this) { #L(it) }", identifier, '/', encodeFn)
                            } else {
                                write("add(#L(#L))", encodeFn, identifier)
                            }
                        } else {
                            // literal
                            val encodeFn = format("#T.Path.encode", RuntimeTypes.Core.Text.Encoding.PercentEncoding)
                            writer.write("add(#L(\"#L\"))", encodeFn, segment.content.toEscapedLiteral())
                        }
                    }
                }
            }

            if (httpTrait.uri.segments.isEmpty()) {
                writer.write("path.trailingSlash = true")
            }
        } else {
            // all literals, inline directly
            val resolvedPath = httpTrait.uri.segments.joinToString(
                separator = "/",
                prefix = "/",
                transform = {
                    it.content.toEscapedLiteral()
                },
            )
            writer.write("path.encoded = \"#L\"", resolvedPath)
        }
    }

    private fun renderQueryParameters(
        ctx: ProtocolGenerator.GenerationContext,
        httpTrait: HttpTrait,
        bindings: List<HttpBindingDescriptor>,
        writer: KotlinWriter,
    ) {
        // literals in the URI
        val queryLiterals = httpTrait.uri.queryLiterals

        // shape bindings
        val queryBindings = bindings.filter { it.location == HttpBinding.Location.QUERY }

        // maps bound via httpQueryParams trait
        val queryMapBindings = bindings.filter { it.location == HttpBinding.Location.QUERY_PARAMS }

        if (queryBindings.isEmpty() && queryLiterals.isEmpty() && queryMapBindings.isEmpty()) return

        if (queryLiterals.isNotEmpty()) {
            writer.withBlock("parameters.decodedParameters {", "}") {
                queryLiterals.forEach { (key, value) -> writer.write("add(#S, #S)", key, value) }
            }
        }

        if (queryBindings.isNotEmpty() || queryMapBindings.isNotEmpty()) {
            writer.withBlock(
                "parameters.decodedParameters(#T.SmithyLabel) {",
                "}",
                RuntimeTypes.Core.Text.Encoding.PercentEncoding,
            ) {
                // render length check if applicable
                queryBindings.forEach { binding -> renderNonBlankGuard(ctx, binding.member, writer) }

                renderStringValuesMapParameters(ctx, queryBindings, writer)

                queryMapBindings.forEach {
                    // either Map<String, String> or Map<String, Collection<String>>
                    // https://awslabs.github.io/smithy/1.0/spec/core/http-traits.html#httpqueryparams-trait
                    val target = ctx.model.expectShape<MapShape>(it.member.target)
                    val valueTarget = ctx.model.expectShape(target.value.target)
                    val fn = when (valueTarget.type) {
                        ShapeType.STRING -> "add"
                        ShapeType.LIST, ShapeType.SET -> "addAll"
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
    }

    // shared implementation for rendering members that belong to StringValuesMap (e.g. Header or Query parameters)
    private fun renderStringValuesMapParameters(
        ctx: ProtocolGenerator.GenerationContext,
        bindings: List<HttpBindingDescriptor>,
        writer: KotlinWriter,
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        HttpStringValuesMapSerializer(ctx, bindings, resolver, defaultTimestampFormat).render(writer)
    }

    /**
     * Render serialization for a member bound with the `httpPayload` trait
     *
     * @param ctx The code generation context
     * @param binding The explicit payload binding
     * @param writer The code writer to render to
     */
    protected fun renderExplicitHttpPayloadSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        binding: HttpBindingDescriptor,
        writer: KotlinWriter,
    ) {
        // explicit payload member as the sole payload
        val memberName = binding.member.defaultName()
        writer.openBlock("if (input.#L != null) {", memberName)

        val target = ctx.model.expectShape(binding.member.target)

        when (target.type) {
            ShapeType.BLOB -> {
                val isBinaryStream = ctx.model.expectShape(binding.member.target).hasTrait<StreamingTrait>()
                if (isBinaryStream) {
                    writer.write("builder.body = input.#L.#T()", memberName, RuntimeTypes.Http.toHttpBody)
                } else {
                    if (ctx.settings.build.generateServiceProject) {
                        writer.write("response = input.#L.decodeToString()", memberName)
                    } else {
                        writer.write("builder.body = #T.fromBytes(input.#L)", RuntimeTypes.Http.HttpBody, memberName)
                    }
                }
            }

            ShapeType.STRING -> {
                val contents = if (target.isEnum) {
                    "$memberName.value"
                } else {
                    memberName
                }
                if (ctx.settings.build.generateServiceProject) {
                    writer.write("response = input.#L", contents)
                } else {
                    writer.write("builder.body = #T.fromBytes(input.#L.#T())", RuntimeTypes.Http.HttpBody, contents, KotlinTypes.Text.encodeToByteArray)
                }
            }

            ShapeType.ENUM ->
                if (ctx.settings.build.generateServiceProject) {
                    writer.write("response = input.#L.value.toString()", memberName)
                } else {
                    writer.write(
                        "builder.body = #T.fromBytes(input.#L.value.#T())",
                        RuntimeTypes.Http.HttpBody,
                        memberName,
                        KotlinTypes.Text.encodeToByteArray,
                    )
                }

            ShapeType.INT_ENUM ->
                if (ctx.settings.build.generateServiceProject) {
                    writer.write("response = input.#L.value.toString()", memberName)
                } else {
                    writer.write(
                        "builder.body = #T.fromBytes(input.#L.value.toString().#T())",
                        RuntimeTypes.Http.HttpBody,
                        memberName,
                        KotlinTypes.Text.encodeToByteArray,
                    )
                }

            ShapeType.STRUCTURE, ShapeType.UNION, ShapeType.DOCUMENT -> {
                val sdg = structuredDataSerializer(ctx)
                val payloadSerializerFn = sdg.payloadSerializer(ctx, binding.member)
                writer.write("val payload = #T(input.#L)", payloadSerializerFn, memberName)
                if (ctx.settings.build.generateServiceProject) {
                    writer.write("response = payload.decodeToString()")
                } else {
                    writer.write("builder.body = #T.fromBytes(payload)", RuntimeTypes.Http.HttpBody)
                }
            }

            else ->
                throw CodegenException("member shape ${binding.member} (${target.type}) serializer not implemented yet")
        }
        writer.closeBlock("}")
    }

    /**
     * Generate request deserializer (HttpDeserialize) for an operation
     */
    private fun generateOperationDeserializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape) {
        val deserializationBindings = if (ctx.settings.build.generateServiceProject) {
            op.input
        } else {
            op.output
        }
        if (!deserializationBindings.isPresent) {
            return
        }
        val deserializationShape = ctx.model.expectShape(deserializationBindings.get())
        val deserializationSymbol = ctx.symbolProvider.toSymbol(deserializationShape)

        // operation output shapes could be re-used across one or more operations. The protocol details may
        // be different though (e.g. uri/method). We need to generate a serializer/deserializer per/operation
        // NOT per input/output shape
        val deserializerSymbol = buildSymbol {
            val definitionFileName = op.deserializerName().replaceFirstChar(Char::uppercaseChar)
            definitionFile = "$definitionFileName.kt"
            name = op.deserializerName()
            namespace = ctx.settings.pkg.serde

            reference(deserializationSymbol, SymbolReference.ContextOption.DECLARE)
        }

        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val bindings = if (ctx.settings.build.generateServiceProject) {
            resolver.requestBindings(op)
        } else {
            resolver.responseBindings(op)
        }

        val serdeMeta = httpDeserializerInfo(ctx, op)

        ctx.delegator.useSymbolWriter(deserializerSymbol) { writer ->
            when (ctx.settings.build.generateServiceProject) {
                true ->
                    writer
                        .write("")
                        .openBlock(
                            "internal class #T {",
                            deserializerSymbol,
                        )
                        .write("")
                        .call { renderServiceHttpDeserialize(ctx, deserializationSymbol, bindings, serdeMeta, op, writer) }
                        .closeBlock("}")
                false ->
                    writer
                        .write("")
                        .openBlock(
                            "internal class #T: #T.#L<#T> {",
                            deserializerSymbol,
                            RuntimeTypes.HttpClient.Operation.HttpDeserializer,
                            serdeMeta.variantName,
                            deserializationSymbol,
                        )
                        .write("")
                        .call { renderHttpDeserialize(ctx, deserializationSymbol, bindings, serdeMeta, op, writer) }
                        .closeBlock("}")
            }
        }
    }

    /**
     * Renders the logic to detect if an HTTP response should be considered an error for this operation
     */
    protected open fun renderIsHttpError(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.addImport(RuntimeTypes.Http.isSuccess)
        writer.withBlock("if (!response.status.#T()) {", "}", RuntimeTypes.Http.isSuccess) {
            val serdeMeta = httpDeserializerInfo(ctx, op)
            if (serdeMeta.isStreaming) {
                writer.write("val payload = response.body.#T()", RuntimeTypes.Http.readAll)
            }
            val errorHandlerFn = operationErrorHandler(ctx, op)
            write("#T(context, call, payload)", errorHandlerFn)
        }
    }

    /**
     * Generate HttpDeserialize for a modeled error (exception)
     */
    private fun generateExceptionDeserializer(ctx: ProtocolGenerator.GenerationContext, shape: StructureShape) {
        val deserializationSymbol = ctx.symbolProvider.toSymbol(shape)
        val exceptionDeserializerSymbols = setOf(
            RuntimeTypes.Core.ExecutionContext,
            RuntimeTypes.Http.Response.HttpResponse,
            RuntimeTypes.Serde.SdkObjectDescriptor,
            RuntimeTypes.Serde.SdkFieldDescriptor,
            RuntimeTypes.Serde.SerialKind,
            RuntimeTypes.Serde.deserializeStruct,
            RuntimeTypes.Http.Response.HttpResponse,
        )

        val deserializerSymbol = buildSymbol {
            val deserializerName = "${deserializationSymbol.name}Deserializer"
            definitionFile = "$deserializerName.kt"
            name = deserializerName
            namespace = ctx.settings.pkg.serde
            reference(deserializationSymbol, SymbolReference.ContextOption.DECLARE)
        }

        // exception deserializers are never streaming
        val serdeMeta = HttpSerdeMeta(false)

        ctx.delegator.useSymbolWriter(deserializerSymbol) { writer ->
            val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
            val bindings = if (ctx.settings.build.generateServiceProject) {
                resolver.requestBindings(shape)
            } else {
                resolver.responseBindings(shape)
            }
            writer
                .addImport(exceptionDeserializerSymbols)
                .write("")
                .openBlock("internal class #T: #T.NonStreaming<#T> {", deserializerSymbol, RuntimeTypes.HttpClient.Operation.HttpDeserializer, deserializationSymbol)
                .write("")
                .call { renderHttpDeserialize(ctx, deserializationSymbol, bindings, serdeMeta, null, writer) }
                .closeBlock("}")
        }
    }

    private fun renderHttpDeserialize(
        ctx: ProtocolGenerator.GenerationContext,
        deserializationSymbol: Symbol,
        bindings: List<HttpBindingDescriptor>,
        serdeMeta: HttpSerdeMeta,
        // this method is shared between operation and exception deserialization. In the case of operations this MUST be set
        op: OperationShape?,
        writer: KotlinWriter,
    ) {
        if (serdeMeta.isStreaming) {
            writer
                .openBlock(
                    "override suspend fun deserialize(context: #T, call: #T): #T {",
                    RuntimeTypes.Core.ExecutionContext,
                    RuntimeTypes.Http.HttpCall,
                    deserializationSymbol,
                )
        } else {
            writer
                .openBlock(
                    "override fun deserialize(context: #T, call: #T, payload: #T?): #T {",
                    RuntimeTypes.Core.ExecutionContext,
                    RuntimeTypes.Http.HttpCall,
                    KotlinTypes.ByteArray,
                    deserializationSymbol,
                )
        }

        writer.write("val response = call.response")
            .call {
                if (deserializationSymbol.shape?.isError == false && op != null) {
                    // handle operation errors
                    renderIsHttpError(ctx, op, writer)
                }
            }
            .write("val builder = #T.Builder()", deserializationSymbol)
            .write("")
            .call {
                // headers
                val headerBindings = bindings
                    .filter { it.location == HttpBinding.Location.HEADER }
                    .sortedBy { it.memberName }

                renderDeserializeHeaders(ctx, headerBindings, writer)

                // prefix headers
                // spec: "Only a single structure member can be bound to httpPrefixHeaders"
                bindings.firstOrNull { it.location == HttpBinding.Location.PREFIX_HEADERS }
                    ?.let {
                        renderDeserializePrefixHeaders(ctx, it, writer)
                    }
            }
            .write("")
            .call {
                if (op != null && op.isOutputEventStream(ctx.model)) {
                    deserializeViaEventStream(ctx, op, writer)
                } else {
                    deserializeViaPayload(ctx, deserializationSymbol, bindings, serdeMeta, op, writer)
                }
            }
            .call {
                bindings.firstOrNull { it.location == HttpBinding.Location.RESPONSE_CODE }
                    ?.let {
                        renderDeserializeResponseCode(ctx, it, writer)
                    }
            }
            // Render client side error correction for `@required` members.
            // NOTE: nested members bound via the document/payload will be handled by the deserializer for the relevant
            // content type. All other members (e.g. bound via REST semantics) will be corrected here.
            .write("builder.correctErrors()")
            .write("return builder.build()")
            .closeBlock("}")
    }

    private fun renderServiceHttpDeserialize(
        ctx: ProtocolGenerator.GenerationContext,
        deserializationSymbol: Symbol,
        bindings: List<HttpBindingDescriptor>,
        serdeMeta: HttpSerdeMeta,
        // this method is shared between operation and exception deserialization. In the case of operations this MUST be set
        op: OperationShape?,
        writer: KotlinWriter,
    ) {
        writer
            .openBlock(
                "public fun deserialize(context: #T, call: #T, payload: #T?): #T {",
                RuntimeTypes.Core.ExecutionContext,
                RuntimeTypes.KtorServerCore.ApplicationCallClass,
                KotlinTypes.ByteArray,
                deserializationSymbol,
            )

        writer.write("val request = call.request")
            .write("val builder = #T.Builder()", deserializationSymbol)
            .write("")
            .call {
                // headers
                val headerBindings = bindings
                    .filter { it.location == HttpBinding.Location.HEADER }
                    .sortedBy { it.memberName }

                renderDeserializeHeaders(ctx, headerBindings, writer)

                // prefix headers
                // spec: "Only a single structure member can be bound to httpPrefixHeaders"
                bindings.firstOrNull { it.location == HttpBinding.Location.PREFIX_HEADERS }
                    ?.let {
                        renderDeserializePrefixHeaders(ctx, it, writer)
                    }
            }
            .write("")
            .call {
                // TODO: will never enter this block. event stream is not in the scope of service generation for now.
                if (op != null && op.isOutputEventStream(ctx.model)) {
                    deserializeViaEventStream(ctx, op, writer)
                } else {
                    deserializeViaPayload(ctx, deserializationSymbol, bindings, serdeMeta, op, writer)
                }
            }
            .call {
                bindings.firstOrNull { it.location == HttpBinding.Location.RESPONSE_CODE }
                    ?.let {
                        renderDeserializeResponseCode(ctx, it, writer)
                    }
            }
            // Render client side error correction for `@required` members.
            // NOTE: nested members bound via the document/payload will be handled by the deserializer for the relevant
            // content type. All other members (e.g. bound via REST semantics) will be corrected here.
            .write("builder.correctErrors()")
            .write("return builder.build()")
            .closeBlock("}")
    }

    /**
     * Deserialize a non-streaming payload
     */
    private fun deserializeViaPayload(
        ctx: ProtocolGenerator.GenerationContext,
        deserializationSymbol: Symbol,
        bindings: List<HttpBindingDescriptor>,
        serdeMeta: HttpSerdeMeta,
        // this method is shared between operation and exception deserialization. In the case of operations this MUST be set
        op: OperationShape?,
        writer: KotlinWriter,
    ) {
        // payload member(s)
        val httpPayload = bindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
        if (httpPayload != null) {
            renderExplicitHttpPayloadDeserializer(ctx, httpPayload, writer)
        } else {
            // Unbound document members that should be deserialized from the document format for the protocol.
            val documentMembers = bindings
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
                    sdg.errorDeserializer(ctx, deserializationSymbol.shape as StructureShape, documentMembers)
                }

                if (!serdeMeta.isStreaming) {
                    writer.withBlock("if (payload != null) {", "}") {
                        write("#T(builder, payload)", bodyDeserializerFn)
                    }
                }
            }
        }
    }

    private fun deserializeViaEventStream(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        val eventStreamDeserializerFn = eventStreamResponseHandler(ctx, op)
        writer.write("#T(builder, call)", eventStreamDeserializerFn)
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
        writer: KotlinWriter,
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        bindings.forEach { hdrBinding ->
            val memberTarget = ctx.model.expectShape(hdrBinding.member.target)
            val memberName = hdrBinding.member.defaultName()
            val headerName = hdrBinding.locationName

            val targetSymbol = ctx.symbolProvider.toSymbol(hdrBinding.member)
            val defaultValuePostfix = if (targetSymbol.isNotNullable && targetSymbol.defaultValue() != null) {
                " ?: ${targetSymbol.defaultValue()}"
            } else {
                ""
            }
            val message = if (ctx.settings.build.generateServiceProject) {
                "request"
            } else {
                "response"
            }
            when (memberTarget) {
                is NumberShape -> {
                    if (memberTarget is IntEnumShape) {
                        val enumSymbol = ctx.symbolProvider.toSymbol(memberTarget)
                        writer.addImport(enumSymbol)
                        writer.write(
                            "builder.#L = $message.headers[#S]?.let { #T.fromValue(it.toInt()) }",
                            memberName,
                            headerName,
                            enumSymbol,
                        )
                    } else {
                        writer.write(
                            "builder.#L = $message.headers[#S]?.#L$defaultValuePostfix",
                            memberName,
                            headerName,
                            stringToNumber(memberTarget),
                        )
                    }
                }
                is BooleanShape -> {
                    writer.write(
                        "builder.#L = $message.headers[#S]?.toBoolean()$defaultValuePostfix",
                        memberName,
                        headerName,
                    )
                }
                is BlobShape -> {
                    writer.write("builder.#L = $message.headers[#S]?.#T()", memberName, headerName, RuntimeTypes.Core.Text.Encoding.decodeBase64)
                }
                is StringShape -> {
                    when {
                        memberTarget.isStringEnumShape -> {
                            val enumSymbol = ctx.symbolProvider.toSymbol(memberTarget)
                            writer.addImport(enumSymbol)
                            writer.write(
                                "builder.#L = $message.headers[#S]?.let { #T.fromValue(it) }",
                                memberName,
                                headerName,
                                enumSymbol,
                            )
                        }
                        memberTarget.hasTrait<MediaTypeTrait>() -> {
                            writer.write("builder.#L = $message.headers[#S]?.#T()", memberName, headerName, RuntimeTypes.Core.Text.Encoding.decodeBase64)
                        }
                        else -> {
                            writer.write("builder.#L = $message.headers[#S]", memberName, headerName)
                        }
                    }
                }
                is TimestampShape -> {
                    val tsFormat = resolver.determineTimestampFormat(
                        hdrBinding.member,
                        HttpBinding.Location.HEADER,
                        defaultTimestampFormat,
                    )
                    writer.write(
                        "builder.#L = $message.headers[#S]?.let { #L }",
                        memberName,
                        headerName,
                        writer.parseInstantExpr("it", tsFormat),
                    )
                }
                is ListShape -> {
                    // member > boolean, number, string, or timestamp
                    // headers are List<String>, get the internal mapping function contents (if any) to convert
                    // to the target symbol type

                    // we also have to handle multiple comma separated values (e.g. 'X-Foo': "1, 2, 3"`)
                    var splitFn = "splitHeaderListValues"
                    val conversion = when (val collectionMemberTarget = ctx.model.expectShape(memberTarget.member.target)) {
                        is BooleanShape -> "it.toBoolean()"
                        is NumberShape -> {
                            if (collectionMemberTarget is IntEnumShape) {
                                val enumSymbol = ctx.symbolProvider.toSymbol(collectionMemberTarget)
                                writer.addImport(enumSymbol)
                                "${enumSymbol.name}.fromValue(it.toInt())"
                            } else {
                                "it." + stringToNumber(collectionMemberTarget)
                            }
                        }
                        is TimestampShape -> {
                            val tsFormat = resolver.determineTimestampFormat(
                                hdrBinding.member,
                                HttpBinding.Location.HEADER,
                                defaultTimestampFormat,
                            )
                            if (tsFormat == TimestampFormatTrait.Format.HTTP_DATE) {
                                splitFn = "splitHttpDateHeaderListValues"
                            }
                            writer.parseInstantExpr("it", tsFormat)
                        }
                        is StringShape -> {
                            when {
                                collectionMemberTarget.isStringEnumShape -> {
                                    val enumSymbol = ctx.symbolProvider.toSymbol(collectionMemberTarget)
                                    writer.addImport(enumSymbol)
                                    "${enumSymbol.name}.fromValue(it)"
                                }
                                collectionMemberTarget.hasTrait<MediaTypeTrait>() -> {
                                    writer.addImport(RuntimeTypes.Core.Text.Encoding.decodeBase64)
                                    "it.decodeBase64()"
                                }
                                else -> ""
                            }
                        }
                        else -> throw CodegenException("invalid member type for header collection: binding: $hdrBinding; member: $memberName")
                    }

                    val mapFn = if (conversion.isNotEmpty()) {
                        "?.map { $conversion }"
                    } else {
                        ""
                    }

                    writer
                        .addImport(splitFn, KotlinDependency.HTTP, subpackage = "util")
                        .write("builder.#L = $message.headers.getAll(#S)?.flatMap(::$splitFn)$mapFn", memberName, headerName)
                }
                else -> throw CodegenException("unknown deserialization: header binding: $hdrBinding; member: `$memberName`")
            }
        }
    }

    private fun renderDeserializePrefixHeaders(
        ctx: ProtocolGenerator.GenerationContext,
        binding: HttpBindingDescriptor,
        writer: KotlinWriter,
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

        val message = if (ctx.settings.build.generateServiceProject) {
            "request"
        } else {
            "response"
        }
        writer.write("val $keyCollName = $message.headers.names()$filter")
        writer.openBlock("if ($keyCollName.isNotEmpty()) {")
            .write("val map = mutableMapOf<String, #T>()", targetValueSymbol)
            .openBlock("for (hdrKey in $keyCollName) {")
            .call {
                val getFn = when (targetValueShape) {
                    is StringShape -> "[hdrKey]"
                    is ListShape -> ".getAll(hdrKey)"
                    else -> throw CodegenException("invalid httpPrefixHeaders usage on ${binding.member}")
                }
                // get()/getAll() returns String? or List<String>?, this shouldn't ever trigger the continue though...
                writer.write("val el = $message.headers$getFn ?: continue")
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
        writer: KotlinWriter,
    ) {
        val memberName = binding.member.defaultName()
        val target = ctx.model.expectShape(binding.member.target)
        val targetSymbol = ctx.symbolProvider.toSymbol(target)
        val message = if (ctx.settings.build.generateServiceProject) {
            "request"
        } else {
            "response"
        }
        // NOTE: we don't need serde metadata to know what to do here. Everything is non-streaming except streaming
        // blob payloads.
        when (target.type) {
            ShapeType.STRING -> {
                writer.write("val contents = payload?.decodeToString()")
                if (target.isEnum) {
                    writer.write("builder.$memberName = contents?.let { #T.fromValue(it) }", targetSymbol)
                } else {
                    writer.write("builder.$memberName = contents")
                }
            }

            ShapeType.ENUM -> {
                writer.write("val contents = payload?.decodeToString()")
                writer.write("builder.#L = contents?.let { #T.fromValue(it) }", memberName, targetSymbol)
            }

            ShapeType.INT_ENUM -> {
                writer.write("val contents = payload?.decodeToString()")
                writer.write("builder.#L = contents?.let { #T.fromValue(it.toInt()) }", memberName, targetSymbol)
            }

            ShapeType.BLOB -> {
                val isBinaryStream = target.hasTrait<StreamingTrait>()
                if (isBinaryStream) {
                    writer.write("builder.#L = $message.body.#T()", memberName, RuntimeTypes.Http.toByteStream)
                } else {
                    writer.write("builder.#L = payload", memberName)
                }
            }

            ShapeType.STRUCTURE, ShapeType.UNION, ShapeType.DOCUMENT -> {
                // delegate to the payload deserializer
                val sdg = structuredDataParser(ctx)
                val payloadDeserializerFn = sdg.payloadDeserializer(ctx, binding.member)

                writer.withBlock("if (payload != null) {", "}") {
                    write("builder.#L = #T(payload)", memberName, payloadDeserializerFn)
                }
            }

            else ->
                throw CodegenException("member shape ${binding.member} (${target.type}) deserializer not implemented")
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
        bindings: List<HttpBindingDescriptor>,
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
    namespace = settings.pkg.serde
    // place error handler in same file as operation deserializer
    definitionFile = "${deserializerName()}.kt"
    renderBy = block
}

private fun renderNonBlankGuard(ctx: ProtocolGenerator.GenerationContext, member: MemberShape, writer: KotlinWriter) {
    if (member.isNonBlankInStruct(ctx)) {
        val memberSymbol = ctx.symbolProvider.toSymbol(member)
        val nullCheck = if (memberSymbol.isNullable) "?" else ""
        writer.write("""require(input.#1L$nullCheck.isNotBlank() == true) { "#1L is bound to the URI and must be a non-blank value" }""", member.defaultName())
    }
}
private fun MemberShape.isNonBlankInStruct(ctx: ProtocolGenerator.GenerationContext): Boolean =
    ctx.model.expectShape(target).isStringShape &&
        getTrait<LengthTrait>()?.min?.getOrNull()?.takeIf { it > 0 } != null

private data class HttpSerdeMeta(val isStreaming: Boolean) {
    /**
     * The name of the HttpSerializer<T>/HttpDeserializer<T> variant
     */
    val variantName: String
        get() = if (isStreaming) "Streaming" else "NonStreaming"
}

private fun httpDeserializerInfo(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): HttpSerdeMeta {
    val deserializationTarget = if (ctx.settings.build.generateServiceProject) {
        op.input
    } else {
        op.output
    }
    val isStreaming = ctx.model.expectShape<StructureShape>(deserializationTarget.get()).hasStreamingMember(ctx.model) ||
        op.isOutputEventStream(ctx.model)

    return HttpSerdeMeta(isStreaming)
}
