/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.toEscapedLiteral
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
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
    protected abstract val defaultTimestampFormat: TimestampFormatTrait.Format

    /**
     * Returns HTTP binding resolver for protocol specified by input.
     */
    abstract fun getProtocolHttpBindingResolver(ctx: ProtocolGenerator.GenerationContext): HttpBindingResolver

    /**
     * Get the [HttpProtocolClientGenerator] to be used to render the implementation of the service client interface
     */
    abstract fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator

    /**
     * Get all of the middleware that should be installed into the operation's middleware stack (`SdkOperationExecution`)
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
    protected open fun getDefaultHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> = listOf()

    /**
     * Render serialize implementation for the given operation's input shape.
     *
     * This function is invoked inside the body of the serialize function which has the following signature:
     *
     * ```
     * fun serializeFoo(context: ExecutionContext, input: Foo): ByteArray {
     *     <-- CURRENT WRITER CONTEXT -->
     * }
     * ```
     *
     * Implementations are expected to render a [ByteArray] as the return value
     *
     * @param ctx the protocol generator context
     * @param op the operation to render serialize for
     * @param writer the writer to render to
     */
    abstract fun renderSerializeOperationBody(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter)

    /**
     * Render deserialize implementation for the given operation's output shape.
     *
     * This function is invoked inside the body of the deserialize function which has the following signature:
     *
     * ```
     * fun deserializeFoo(builder: Foo.DslBuilder, payload: ByteArray) {
     *     <-- CURRENT WRITER CONTEXT -->
     * }
     * ```
     *
     * Implementations are expected to instantiate an appropriate deserializer for the protocol and deserialize
     * the output shape from the payload using the builder passed in.
     *
     * @param ctx the protocol generator context
     * @param op the operation to render deserialize for
     * @param writer the writer to render to
     */
    abstract fun renderDeserializeOperationBody(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter)

    /**
     * Render serialize implementation for the given document shape
     *
     * This function is invoked inside the body of the serialize function which has the following signature:
     *
     * ```
     * suspend fun serializeFoo(serializer: Serializer, input: Foo) {
     *     <-- CURRENT WRITER CONTEXT -->
     * }
     * ```
     *
     * Implementations are expected to serialize the input to the given serializer
     *
     * @param ctx the protocol generator context
     * @param shape the shape to render serialize for
     * @param writer the writer to render to
     */
    abstract fun renderSerializeDocumentBody(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter)

    /**
     * Render deserialize implementation for the given document shape.
     *
     * This function is invoked inside the body of the deserialize function which has the following signature:
     *
     * ```
     * suspend fun deserializeFoo(deserializer: Deserializer): Foo {
     *     <-- CURRENT WRITER CONTEXT -->
     * }
     * ```
     *
     * Implementations are expected to deserialize an instance of the shape using the deserializer passed in and
     * return an instance to the caller
     *
     * @param ctx the protocol generator context
     * @param shape the document to render deserialize for
     * @param writer the writer to render to
     */
    abstract fun renderDeserializeDocumentBody(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter)

    /**
     * Render deserialize implementation for the given exception (error) shape.
     *
     * This function is invoked inside the body of the deserialize function which has the following signature:
     *
     * ```
     * fun deserializeFooError(builder: FooError.DslBuilder, payload: ByteArray) {
     *     <-- CURRENT WRITER CONTEXT -->
     * }
     * ```
     *
     * Implementations are expected to instantiate an appropriate deserializer for the protocol and deserialize
     * the error shape from the payload using the builder passed in.
     *
     * @param ctx the protocol generator context
     * @param shape the (error) shape to render deserialize for
     * @param writer the writer to render to
     */
    abstract fun renderDeserializeException(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter)

    override fun generateSerializers(ctx: ProtocolGenerator.GenerationContext) {
        val resolver = getProtocolHttpBindingResolver(ctx)
        val httpOperations = resolver.bindingOperations()
        // render HttpSerialize for all operation inputs
        httpOperations.forEach { operation ->
            generateOperationSerializer(ctx, operation)
        }

        // generate serde for all shapes that appear as nested on any operation input
        // these types are `SdkSerializable` not `HttpSerialize`
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringSerializers = serdeIndex.requiresDocumentSerializer(httpOperations)
        generateDocumentSerializers(ctx, shapesRequiringSerializers)
    }

    override fun generateDeserializers(ctx: ProtocolGenerator.GenerationContext) {
        val resolver = getProtocolHttpBindingResolver(ctx)
        // render HttpDeserialize for all operation outputs
        val httpOperations = resolver.bindingOperations()
        httpOperations.forEach { operation ->
            generateOperationDeserializer(ctx, operation)
        }

        // generate HttpDeserialize for exception types
        val modeledErrors = httpOperations.flatMap { it.errors }.map { ctx.model.expectShape(it) as StructureShape }.toSet()
        modeledErrors.forEach { generateExceptionDeserializer(ctx, it) }

        // generate serde for all shapes that appear as nested on any operation output
        // these types are independent document deserializers, they do not implement `HttpDeserialize`
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDeserializers = serdeIndex.requiresDocumentDeserializer(httpOperations)
        generateDocumentDeserializers(ctx, shapesRequiringDeserializers)
    }

    override fun generateProtocolClient(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = ctx.symbolProvider.toSymbol(ctx.service)
        ctx.delegator.useFileWriter("Default${symbol.name}.kt", ctx.settings.pkg.name) { writer ->
            val clientGenerator = getHttpProtocolClientGenerator(ctx)
            clientGenerator.render(writer)
        }
    }

    /**
     * Generate `SdkSerializable` serializer for all shapes in the set
     */
    private fun generateDocumentSerializers(ctx: ProtocolGenerator.GenerationContext, shapes: Set<Shape>) {
        for (shape in shapes) {
            val symbol = ctx.symbolProvider.toSymbol(shape)

            val serializerSymbol = buildSymbol {
                definitionFile = "${symbol.name}DocumentSerializer.kt"
                name = symbol.documentSerializerName()
                namespace = "${ctx.settings.pkg.name}.transform"

                // serializer class for the shape takes the shape's symbol as input
                // ensure we get an import statement to the symbol from the .model package
                reference(symbol, SymbolReference.ContextOption.DECLARE)
            }

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
        writer
            .addImport(RuntimeTypes.Serde.serializeStruct, RuntimeTypes.Serde.SdkFieldDescriptor, RuntimeTypes.Serde.SdkObjectDescriptor, RuntimeTypes.Serde.Serializer)
            .write("")
            .openBlock("internal fun #T(serializer: #T, input: #T) {", serializerSymbol, RuntimeTypes.Serde.Serializer, symbol)
            .call {
                renderSerializeDocumentBody(ctx, shape, writer)
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

        // operation input shapes could be re-used across one or more operations. The protocol details may
        // be different though (e.g. uri/method). We need to generate a serializer/deserializer per/operation
        // NOT per input/output shape
        val serializerSymbol = buildSymbol {
            definitionFile = "${op.serializerName().capitalize()}.kt"
            name = op.serializerName()
            namespace = "${ctx.settings.pkg.name}.transform"

            reference(inputSymbol, SymbolReference.ContextOption.DECLARE)
        }

        val resolver = getProtocolHttpBindingResolver(ctx)
        val requestBindings = resolver.requestBindings(op)
        ctx.delegator.useShapeWriter(serializerSymbol) { writer ->
            // import all of http, http.request, and serde packages. All serializers requires one or more of the symbols
            // and most require quite a few. Rather than try and figure out which specific ones are used just take them
            // all to ensure all the various DSL builders are available, etc
            writer
                .addImport(RuntimeTypes.Http.allSymbols, RuntimeTypes.Http.Request.allSymbols)
                .addImport(RuntimeTypes.Http.Operation.HttpSerialize)
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
                .callIf(requiresBodySerde(ctx, requestBindings)) {
                    // render a function responsible for serializing the members bound to the payload
                    writer.write("")
                        .openBlock(
                            "private fun #L(context: #T, input: #T): ByteArray {", "}",
                            op.bodySerializerName(), RuntimeTypes.Core.ExecutionContext, inputSymbol
                        ) {
                            renderSerializeOperationBody(ctx, op, writer)
                        }
                }
        }
    }

    protected open fun renderHttpSerialize(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx)
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
                    writer.withBlock("builder.headers {", "}") {
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
        val resolver = getProtocolHttpBindingResolver(ctx)
        val requestBindings = resolver.requestBindings(op)
        if (!hasHttpBody(requestBindings)) return

        writer.addImport(RuntimeTypes.Http.ByteArrayContent)

        // payload member(s)
        val httpPayload = requestBindings.firstOrNull { it.location == HttpBinding.Location.PAYLOAD }
        if (httpPayload != null) {
            renderExplicitHttpPayloadSerializer(ctx, op, httpPayload, writer)
        } else {
            // Unbound document members that should be serialized into the document format for the protocol.
            // delegate to the generate operation body serializer function
            writer.write("val payload = #L(context, input)", op.bodySerializerName())
                .write("builder.body = ByteArrayContent(payload)")
        }

        writer.openBlock("if (builder.body !is #T.Empty) {", "}", RuntimeTypes.Http.HttpBody) {
            val contentType = resolver.determineRequestContentType(op)
            writer.write("builder.headers[\"Content-Type\"] = #S", contentType)
        }
    }

    // replace labels with any path bindings
    protected open fun renderUri(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter
    ) {
        val resolver = getProtocolHttpBindingResolver(ctx)
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

                        val encodeSymbol = RuntimeTypes.Http.EncodeLabel
                        writer.addImport(encodeSymbol)
                        val encodeFn = if (segment.isGreedyLabel) "${encodeSymbol.name}(greedy = true)" else "${encodeSymbol.name}()"
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

        writer.withBlock("parameters {", "}") {
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
        val resolver = getProtocolHttpBindingResolver(ctx)
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
        op: OperationShape,
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
                    writer.write("builder.body = input.#L.toHttpBody() ?: #T.Empty", memberName, RuntimeTypes.Http.HttpBody)
                } else {
                    writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.content", "ByteArrayContent")
                    writer.write("builder.body = ByteArrayContent(input.#L)", memberName)
                }
            }
            ShapeType.STRING -> {
                writer.addImport("${KotlinDependency.CLIENT_RT_HTTP.namespace}.content", "ByteArrayContent")
                val contents = if (target.isEnum) {
                    "$memberName.value"
                } else {
                    memberName
                }
                writer.write("builder.body = ByteArrayContent(input.#L.toByteArray())", contents)
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                // delegate to the generated operation body serializer function
                writer.write("val payload = #L(context, input)", op.bodySerializerName())
                    .write("builder.body = ByteArrayContent(payload)")
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
            definitionFile = "${op.deserializerName().capitalize()}.kt"
            name = op.deserializerName()
            namespace = "${ctx.settings.pkg.name}.transform"

            reference(outputSymbol, SymbolReference.ContextOption.DECLARE)
        }

        val resolver = getProtocolHttpBindingResolver(ctx)
        val responseBindings = resolver.responseBindings(op)
        ctx.delegator.useShapeWriter(deserializerSymbol) { writer ->
            // import all of http, http.response , and serde packages. All serializers requires one or more of the symbols
            // and most require quite a few. Rather than try and figure out which specific ones are used just take them
            // all to ensure all the various DSL builders are available, etc
            writer
                .addImport(RuntimeTypes.Http.allSymbols, RuntimeTypes.Http.Operation.allSymbols, RuntimeTypes.Http.Response.allSymbols)
                .write("")
                .openBlock(
                    "internal class #T: #T<#T> {",
                    deserializerSymbol,
                    RuntimeTypes.Http.Operation.HttpDeserialize,
                    outputSymbol
                )
                .write("")
                .call {
                    renderHttpDeserialize(ctx, outputSymbol, responseBindings, op.bodyDeserializerName(), writer)
                }
                .closeBlock("}")
                .callIf(requiresBodySerde(ctx, responseBindings)) {
                    // render a function responsible for deserializing the members bound to the payload
                    // this function is delegated to by the `HttpDeserialize` implementation generated
                    writer.write("")
                        .openBlock("private suspend fun #L(builder: #T.DslBuilder, payload: ByteArray) {", "}", op.bodyDeserializerName(), outputSymbol) {
                            renderDeserializeOperationBody(ctx, op, writer)
                        }
                }
        }
    }

    /**
     * Generate HttpDeserialize for a modeled error (exception)
     */
    private fun generateExceptionDeserializer(ctx: ProtocolGenerator.GenerationContext, shape: StructureShape) {
        val outputSymbol = ctx.symbolProvider.toSymbol(shape)

        val deserializerSymbol = buildSymbol {
            val deserializerName = "${outputSymbol.name}Deserializer"
            definitionFile = "$deserializerName.kt"
            name = deserializerName
            namespace = "${ctx.settings.pkg.name}.transform"
            reference(outputSymbol, SymbolReference.ContextOption.DECLARE)
        }

        ctx.delegator.useShapeWriter(deserializerSymbol) { writer ->
            val resolver = getProtocolHttpBindingResolver(ctx)
            val responseBindings = resolver.responseBindings(shape)
            writer
                .addImport(RuntimeTypes.Serde.allSymbols, RuntimeTypes.Http.allSymbols)
                .addImport(RuntimeTypes.Http.Response.HttpResponse, RuntimeTypes.Http.Operation.HttpDeserialize)
                .write("")
                .openBlock("internal class #T: #T<#T> {", deserializerSymbol, RuntimeTypes.Http.Operation.HttpDeserialize, outputSymbol)
                .write("")
                .call {
                    renderHttpDeserialize(ctx, outputSymbol, responseBindings, outputSymbol.errorDeserializerName(), writer)
                }
                .closeBlock("}")
                .callIf(requiresBodySerde(ctx, responseBindings)) {
                    // NOTE: Exceptions get their own deserializer the same way that operations do because an exception
                    // shape can be re-used as a document member.
                    writer.write("")
                        .openBlock(
                            "private suspend fun #L(builder: #L, payload: ByteArray) {", "}",
                            outputSymbol.errorDeserializerName(),
                            "${outputSymbol.name}.DslBuilder",
                        ) {
                            renderDeserializeException(ctx, shape, writer)
                        }
                }
        }
    }

    private fun renderHttpDeserialize(
        ctx: ProtocolGenerator.GenerationContext,
        outputSymbol: Symbol,
        responseBindings: List<HttpBindingDescriptor>,
        bodyDeserializerName: String,
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
            .write("val builder = #T.builder()", outputSymbol)
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
                    renderExplicitHttpPayloadDeserializer(ctx, httpPayload, bodyDeserializerName, writer)
                } else {
                    // Unbound document members that should be deserialized from the document format for the protocol.
                    // The generated code is the same across protocols and the serialization provider instance
                    // passed into the function is expected to handle the formatting required by the protocol
                    val documentMembers = responseBindings
                        .filter { it.location == HttpBinding.Location.DOCUMENT }
                        .sortedBy { it.memberName }
                        .map { it.member }

                    if (documentMembers.isNotEmpty()) {
                        // FIXME - we should not be slurping the entire contents into memory, instead our deserializers
                        // should work off of an SdkByteReadChannel
                        writer.write("val payload = response.body.readAll()")
                        writer.withBlock("if (payload != null) {", "}") {
                            writer.write("#L(builder, payload)", bodyDeserializerName)
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
        val resolver = getProtocolHttpBindingResolver(ctx)
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
                        .addImport("decodeBase64", KotlinDependency.CLIENT_RT_UTILS)
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
                                .addImport("decodeBase64", KotlinDependency.CLIENT_RT_UTILS)
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
                                    writer.addImport("decodeBase64", KotlinDependency.CLIENT_RT_UTILS)
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
                        .addImport(splitFn, KotlinDependency.CLIENT_RT_HTTP, subpackage = "util")
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
        bodyDeserializerName: String,
        writer: KotlinWriter
    ) {
        val memberName = binding.member.defaultName()
        val target = ctx.model.expectShape(binding.member.target)
        val targetSymbol = ctx.symbolProvider.toSymbol(target)
        when (target.type) {
            ShapeType.STRING -> {
                writer.write("val contents = response.body.readAll()?.decodeToString()")
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
                    "toByteStream()"
                } else {
                    "readAll()"
                }
                writer.write("builder.$memberName = response.body.$conversion")
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                // delegate to the payload deserializer
                writer.write("val payload = response.body.readAll()")
                writer.withBlock("if (payload != null) {", "}") {
                    write("#L(builder, payload)", bodyDeserializerName)
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
     * Generate deserializer for all shapes in the set
     */
    private fun generateDocumentDeserializers(ctx: ProtocolGenerator.GenerationContext, shapes: Set<Shape>) {
        for (shape in shapes) {
            val symbol = ctx.symbolProvider.toSymbol(shape)
            val deserializerSymbol = buildSymbol {
                definitionFile = "${symbol.name}DocumentDeserializer.kt"
                name = symbol.documentDeserializerName()
                namespace = "${ctx.settings.pkg.name}.transform"

                // deserializer class for the shape outputs the shape's symbol
                // ensure we get an import statement to the symbol from the .model package
                reference(symbol, SymbolReference.ContextOption.DECLARE)
            }

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
        writer
            .addImport(RuntimeTypes.Serde.allSymbols)
            .write("")
            .openBlock("internal suspend fun #T(deserializer: #T): #T {", deserializerSymbol, RuntimeTypes.Serde.Deserializer, symbol)
            .call {
                if (shape.isUnionShape) {
                    writer.write("var value: #T? = null", symbol)
                    renderDeserializeDocumentBody(ctx, shape, writer)
                    writer.write("return value ?: throw #T(\"Deserialized value unexpectedly null: ${symbol.name}\")", RuntimeTypes.Serde.DeserializationException)
                } else {
                    writer.write("val builder = #T.builder()", symbol)
                    renderDeserializeDocumentBody(ctx, shape, writer)
                    writer.write("return builder.build()")
                }
            }
            .closeBlock("}")
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
            hasHttpBody(bindings)
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

// test if the request/response bindings have any members bound to the HTTP payload (body)
private fun hasHttpBody(bindings: List<HttpBindingDescriptor>): Boolean =
    bindings.any { it.location == HttpBinding.Location.PAYLOAD || it.location == HttpBinding.Location.DOCUMENT }

/**
 * Return member shapes bound to the DOCUMENT
 */
fun List<HttpBindingDescriptor>.filterDocumentBoundMembers(): List<MemberShape> =
    filter { it.location == HttpBinding.Location.DOCUMENT }
        .sortedBy { it.memberName }
        .map { it.member }
