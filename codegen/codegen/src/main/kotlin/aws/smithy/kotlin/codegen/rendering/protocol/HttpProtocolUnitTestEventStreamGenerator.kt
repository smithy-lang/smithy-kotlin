/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.protocol

import aws.smithy.kotlin.codegen.core.KotlinDependency
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.declareSection
import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.model.findStreamingMember
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.rendering.ShapeValueGenerator
import aws.smithy.kotlin.codegen.rendering.endpoints.EndpointProviderGenerator
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.BlobShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase
import software.amazon.smithy.protocoltests.traits.eventstream.Event
import software.amazon.smithy.protocoltests.traits.eventstream.EventHeaderValue
import software.amazon.smithy.protocoltests.traits.eventstream.EventStreamTestCase
import software.amazon.smithy.protocoltests.traits.eventstream.EventType
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait
import java.util.Base64

/**
 * Generates protocol unit tests for `eventStreamTests` cases. Each test drives the generated client against a mock
 * engine which records the request event stream and replays the response event stream.
 */
open class HttpProtocolUnitTestEventStreamGenerator protected constructor(builder: Builder) {
    protected val ctx: ProtocolGenerator.GenerationContext = requireNotNull(builder.ctx) { "protocol generator ctx is required" }
    protected val symbolProvider: SymbolProvider = requireNotNull(builder.symbolProvider) { "symbol provider is required" }
    protected val model: Model = requireNotNull(builder.model) { "model is required" }
    protected val testCases: List<EventStreamTestCase> = requireNotNull(builder.testCases) { "list of test cases is required" }
    protected val operation: OperationShape = requireNotNull(builder.operation) { "operation shape is required" }
    protected val writer: KotlinWriter = requireNotNull(builder.writer) { "writer is required" }
    protected val serviceShape: ServiceShape = requireNotNull(builder.service) { "service shape is required" }

    private val inputShape: StructureShape? = operation.input.map { model.expectShape<StructureShape>(it) }.orElse(null)
    private val outputShape: StructureShape? = operation.output.map { model.expectShape<StructureShape>(it) }.orElse(null)
    private val inputStreamMember: MemberShape? = inputShape?.findStreamingMember(model)
    private val outputStreamMember: MemberShape? = outputShape?.findStreamingMember(model)

    /**
     * Render a test class and unit tests for the specified [testCases]
     */
    fun renderTestClass(testClassName: String) {
        writer.addImport(KotlinDependency.KOTLIN_TEST.namespace, "Test")

        writer.write("")
            .withBlock("class #L {", "}", testClassName) {
                testCases.forEachIndexed { idx, test ->
                    if (idx > 0) write("")
                    renderTestFunction(test)
                }
            }
    }

    private fun renderTestFunction(test: EventStreamTestCase) {
        test.documentation.ifPresent { writer.dokka(it) }
        writer.write("@Test")
        writer.withBlock("fun `#L`() = eventStreamTest {", "}", test.id) {
            renderTestBody(test)
        }
    }

    protected open fun renderTestBody(test: EventStreamTestCase) {
        writer.addImport(KotlinDependency.SMITHY_TEST.namespace, "*")
        writer.addImport(KotlinDependency.KOTLIN_TEST.namespace, "*")
        writer.dependencies.addAll(KotlinDependency.SMITHY_TEST.dependencies)
        writer.dependencies.addAll(KotlinDependency.KOTLIN_TEST.dependencies)

        val requestEvents = test.events.filter { it.type == EventType.REQUEST }
        val responseEvents = test.events.filter { it.type == EventType.RESPONSE }

        // a request-side failure (e.g. a serialization error) leaves nothing meaningful to assert about the request
        if (test.expectation.isSuccess) {
            test.initialRequest.ifPresent { renderExpectedInitialRequest(test, it) }
            requestEvents.forEach(::renderExpectedRequestEvent)
        }

        (test.initialResponse.orElse(null) ?: defaultInitialResponse(test))?.let(::renderInitialResponse)
        responseEvents.forEach(::renderResponseEvent)

        writer.write("")
        renderOperationBlock(test, requestEvents, responseEvents)
    }

    private fun renderExpectedInitialRequest(test: EventStreamTestCase, node: ObjectNode) {
        val request = HttpRequestTestCase.fromNode(node.withTestIdentity(test))
        writer.addImport(KotlinDependency.HTTP.namespace, "HttpMethod")
        writer.withBlock("expectedInitialRequest {", "}") {
            write("method = HttpMethod.#L", request.method.uppercase())
            write("uri = #S", request.uri)
            renderStringPairs("queryParams", request.queryParams.map { it.substringBefore("=") to it.substringAfter("=", "") }, mapStyle = false)
            renderStringList("forbiddenQueryParams", request.forbidQueryParams)
            renderStringList("requiredQueryParams", request.requireQueryParams)
            renderStringPairs("headers", request.headers.toList(), mapStyle = true)
            renderStringList("forbiddenHeaders", request.forbidHeaders)
            renderStringList("requiredHeaders", request.requireHeaders)
            request.body.ifPresent { body ->
                val mediaType = request.bodyMediaType.orElse("")
                if (mediaType.isNotEmpty()) write("bodyMediaType = #S", mediaType)
                if (body.isBlank()) {
                    write("bodyAssert = ::assertEmptyBody")
                } else {
                    write("body = #S", body)
                    write("bodyAssert = #L", if (mediaType.isJsonMediaType) "::assertJsonBodiesEqual" else "::assertBytesEqual")
                }
            }
        }
    }

    private fun renderExpectedRequestEvent(event: Event) {
        writer.withBlock("expectedRequestEvent {", "}") {
            renderHeaderValues(event.headers)
            renderStringList("forbiddenHeaders", event.forbidHeaders)
            renderStringList("requiredHeaders", event.requireHeaders)
            event.body.ifPresent { write("body = #S", it) }
            event.bodyMediaType.ifPresent { write("bodyMediaType = #S", it) }
        }
    }

    private fun renderInitialResponse(node: ObjectNode) {
        // mirrors `smithy.test#InitialHttpResponse`
        val code = node.expectNumberMember("code").value.toInt()
        val headers = node.getObjectMember("headers").map { obj ->
            obj.members.map { (key, value) -> key.value to value.expectStringNode().value }
        }.orElse(emptyList())
        val body = node.getStringMember("body").map { it.value }.orElse(null)

        writer.addImport(KotlinDependency.HTTP.namespace, "HttpStatusCode")
        writer.withBlock("initialResponse {", "}") {
            write("statusCode = HttpStatusCode.fromValue(#L)", code)
            renderStringPairs("headers", headers, mapStyle = true)
            body?.let { write("body = #S", it) }
        }
    }

    /**
     * The initial response to use when a test case omits `initialResponse`. Protocols whose clients reject responses
     * missing protocol-required headers (e.g. `smithy-protocol` for rpcv2Cbor) supply them here.
     */
    protected open fun defaultInitialResponse(test: EventStreamTestCase): ObjectNode? = when (test.protocol) {
        Rpcv2CborTrait.ID ->
            Node.objectNodeBuilder()
                .withMember("code", 200)
                .withMember("headers", Node.objectNode().withMember("smithy-protocol", "rpc-v2-cbor"))
                .build()
        else -> null
    }

    private fun renderResponseEvent(event: Event) {
        // per the spec, the encoded bytes take precedence over the individual properties for deserialization tests
        val bytes = event.bytes.orElse(null)
        if (bytes != null) {
            writer.write(
                "responseEvent(#S.#T())",
                Base64.getEncoder().encodeToString(bytes),
                RuntimeTypes.Core.Text.Encoding.decodeBase64Bytes,
            )
            return
        }

        writer.withBlock("responseEvent {", "}") {
            renderHeaderValues(event.headers)
            event.body.ifPresent { body ->
                // binary bodies (e.g. CBOR) are base64-encoded in the test case
                if (event.bodyMediaType.orElse("").isBinaryMediaType) {
                    write("body = #S.#T()", body, RuntimeTypes.Core.Text.Encoding.decodeBase64Bytes)
                } else {
                    write("body = #S.encodeToByteArray()", body)
                }
            }
        }
    }

    private fun renderOperationBlock(test: EventStreamTestCase, requestEvents: List<Event>, responseEvents: List<Event>) {
        writer.withBlock("operation { mockEngine ->", "}") {
            val inputParamName = inputShape?.let {
                renderInput(test, it, requestEvents)
                "input"
            } ?: ""

            val service = symbolProvider.toSymbol(serviceShape)
            withBlock("val service = #L {", "}", service.name) {
                renderConfigureServiceClient(test)
            }

            val failure = test.expectation.failure.orElse(null)
            if (failure == null) {
                renderServiceCall(test, inputParamName, responseEvents)
            } else {
                val errorSymbol = failure.errorId.map { symbolProvider.toSymbol(model.expectShape(it)) }.orElse(null)
                val assertion = if (errorSymbol != null) {
                    writer.format("assertFailsWith<#T>", errorSymbol)
                } else {
                    "assertFails"
                }
                withBlock("#L {", "}", assertion) {
                    renderServiceCall(test, inputParamName, responseEvents)
                }
            }
        }
    }

    private fun renderInput(test: EventStreamTestCase, shape: StructureShape, requestEvents: List<Event>) {
        val inputSymbol = symbolProvider.toSymbol(shape)
        val generator = ShapeValueGenerator(model, symbolProvider, explicitReceiver = true)

        writer.withBlock("val input = #T {", "}", inputSymbol) {
            test.initialRequestParams.ifPresent { generator.writeShapeValues(writer, shape, it) }

            val streamMember = inputStreamMember
            // events without params are protocol-generated (e.g. an RPC initial-request event), not caller-supplied
            val callerEvents = requestEvents.mapNotNull { it.params.orElse(null) }
            if (streamMember != null && callerEvents.isNotEmpty()) {
                val unionShape = model.expectShape<UnionShape>(streamMember.target)
                withBlock("this.#L = #T(", ")", streamMember.defaultName(), RuntimeTypes.KotlinxCoroutines.Flow.flowOf) {
                    callerEvents.forEachIndexed { idx, params ->
                        generator.instantiateShapeInline(writer, unionShape, params)
                        writer.write(if (idx < callerEvents.size - 1) "," else "")
                    }
                }
            }
        }
    }

    private fun renderServiceCall(test: EventStreamTestCase, inputParamName: String, responseEvents: List<Event>) {
        val opName = operation.defaultName()
        val streamMember = outputStreamMember

        if (streamMember == null) {
            writer.write("service.#L(#L)", opName, inputParamName)
            return
        }

        writer.withBlock("service.#L(#L) { actualResult ->", "}", opName, inputParamName) {
            // consume the stream even when no events are expected so that deserialization failures surface
            writer.addImport("kotlinx.coroutines.flow", "toList")
            if (test.expectation.isSuccess) {
                write("val actualEvents = actualResult.#L?.toList().orEmpty()", streamMember.defaultName())
                renderEventAssertions(streamMember, responseEvents)
                test.initialResponseParams.ifPresent { renderInitialResponseAssertions(streamMember, it) }
            } else {
                write("actualResult.#L?.toList()", streamMember.defaultName())
            }
        }
    }

    private fun renderEventAssertions(streamMember: MemberShape, responseEvents: List<Event>) {
        val unionShape = model.expectShape<UnionShape>(streamMember.target)
        val unionSymbol = symbolProvider.toSymbol(unionShape)
        val generator = ShapeValueGenerator(model, symbolProvider, explicitReceiver = true)

        // unmodeled events are not surfaced as typed values, so only modeled events are compared
        val modeledEvents = responseEvents.mapNotNull { it.params.orElse(null) }
        if (modeledEvents.isEmpty()) {
            writer.write("assertEquals(emptyList(), actualEvents)")
            return
        }

        writer.withBlock("val expectedEvents = listOf<#T>(", ")", unionSymbol) {
            modeledEvents.forEachIndexed { idx, params ->
                generator.instantiateShapeInline(writer, unionShape, params)
                writer.write(if (idx < modeledEvents.size - 1) "," else "")
            }
        }
        writer.write("assertEquals(expectedEvents, actualEvents)")
    }

    private fun renderInitialResponseAssertions(streamMember: MemberShape, params: ObjectNode) {
        val shape = outputShape ?: return
        writer.writeInline("val expectedResult = ")
        ShapeValueGenerator(model, symbolProvider, explicitReceiver = true).instantiateShapeInline(writer, shape, params)
        writer.write("")

        shape.members().filter { it != streamMember }.forEach { member ->
            val name = member.defaultName()
            if (model.expectShape(member.target) is BlobShape) {
                writer.write("assertContentEquals(expectedResult.#1L, actualResult.#1L)", name)
            } else {
                writer.write("assertEquals(expectedResult.#1L, actualResult.#1L)", name)
            }
        }
    }

    /**
     * Configure the service client before executing the operation for the test. By default this function configures
     * the mock engine and (when the model has no endpoint rules) a static endpoint.
     */
    open fun renderConfigureServiceClient(test: EventStreamTestCase) {
        // reuse the request test section so that integrations (credentials, region, etc.) apply here too
        writer.declareSection(
            HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient,
            mapOf(
                HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient.Context to ctx,
                HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient.Operation to operation,
            ),
        ) {
            writer.write("httpClient = mockEngine")

            if (!serviceShape.hasTrait<EndpointRuleSetTrait>()) {
                writer.write(
                    "endpointProvider = #T { #T(#S) }",
                    EndpointProviderGenerator.getSymbol(ctx.settings),
                    RuntimeTypes.SmithyClient.Endpoints.Endpoint,
                    "https://hostname",
                )
            }
        }
    }

    private fun renderHeaderValues(headers: Map<String, EventHeaderValue<*>>) {
        if (headers.isEmpty()) return
        writer.addImport(RuntimeTypes.AwsEventStream.HeaderValue)
        writer.withBlock("headers = mapOf(", ")") {
            headers.entries.forEachIndexed { idx, (name, value) ->
                val suffix = if (idx < headers.size - 1) "," else ""
                write("#S to #L$suffix", name, formatHeaderValue(value))
            }
        }
    }

    private fun formatHeaderValue(value: EventHeaderValue<*>): String = when (value.type) {
        EventHeaderValue.Type.BOOLEAN -> "HeaderValue.Bool(${value.asBoolean()})"
        EventHeaderValue.Type.BYTE -> "HeaderValue.Byte(${value.asByte()}.toUByte())"
        EventHeaderValue.Type.SHORT -> "HeaderValue.Int16(${value.asShort()})"
        EventHeaderValue.Type.INTEGER -> "HeaderValue.Int32(${value.asInteger()})"
        EventHeaderValue.Type.LONG -> "HeaderValue.Int64(${value.asLong()}L)"
        EventHeaderValue.Type.BLOB -> writer.format(
            "HeaderValue.ByteArray(#S.#T())",
            Base64.getEncoder().encodeToString(value.asBlob()),
            RuntimeTypes.Core.Text.Encoding.decodeBase64Bytes,
        )
        EventHeaderValue.Type.STRING -> writer.format("HeaderValue.String(#S)", value.asString())
        EventHeaderValue.Type.TIMESTAMP -> writer.format(
            "HeaderValue.Timestamp(#T.#T(#L))",
            RuntimeTypes.Core.Instant,
            RuntimeTypes.Core.fromEpochMilliseconds,
            value.asTimestamp().toEpochMilli(),
        )
        null -> error("event header value has no type")
    }

    private fun KotlinWriter.renderStringList(name: String, values: List<String>) {
        if (values.isEmpty()) return
        write("#L = listOf(#L)", name, values.joinToString(", ") { format("#S", it) })
    }

    private fun KotlinWriter.renderStringPairs(name: String, pairs: List<Pair<String, String>>, mapStyle: Boolean) {
        if (pairs.isEmpty()) return
        withBlock("#L = #L(", ")", name, if (mapStyle) "mapOf" else "listOf") {
            pairs.forEachIndexed { idx, (key, value) ->
                val suffix = if (idx < pairs.size - 1) "," else ""
                write("#S to #S$suffix", key, value)
            }
        }
    }

    // initial message nodes omit the identity members required to parse them as HTTP test cases
    private fun ObjectNode.withTestIdentity(test: EventStreamTestCase): ObjectNode = toBuilder()
        .withMember("id", Node.from(test.id))
        .withMember("protocol", Node.from(test.protocol.toString()))
        .build()

    private val String.isJsonMediaType: Boolean
        get() = lowercase().let { it == "application/json" || it.endsWith("+json") }

    private val String.isBinaryMediaType: Boolean
        get() = lowercase() == "application/cbor"

    open class Builder {
        var ctx: ProtocolGenerator.GenerationContext? = null
        var symbolProvider: SymbolProvider? = null
        var model: Model? = null
        var testCases: List<EventStreamTestCase>? = null
        var operation: OperationShape? = null
        var writer: KotlinWriter? = null
        var service: ServiceShape? = null

        fun ctx(ctx: ProtocolGenerator.GenerationContext): Builder = apply { this.ctx = ctx }
        fun symbolProvider(provider: SymbolProvider): Builder = apply { this.symbolProvider = provider }
        fun model(model: Model): Builder = apply { this.model = model }
        fun testCases(testCases: List<EventStreamTestCase>): Builder = apply { this.testCases = testCases }
        fun operation(operation: OperationShape): Builder = apply { this.operation = operation }
        fun writer(writer: KotlinWriter): Builder = apply { this.writer = writer }
        fun service(service: ServiceShape): Builder = apply { this.service = service }
        open fun build(): HttpProtocolUnitTestEventStreamGenerator = HttpProtocolUnitTestEventStreamGenerator(this)
    }
}
