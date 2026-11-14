/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.awsprotocol.eventstream.Header
import aws.smithy.kotlin.runtime.awsprotocol.eventstream.HeaderValue
import aws.smithy.kotlin.runtime.awsprotocol.eventstream.Message
import aws.smithy.kotlin.runtime.awsprotocol.eventstream.decodeFrames
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CHUNK_SIGNATURE_HEADER = ":chunk-signature"

/**
 * Setup a [Smithy event stream test](https://smithy.io/2.0/additional-specs/event-stream-protocol-compliance-tests.html).
 *
 * The mock engine reads the entire request body, decodes it as event stream frames (removing any SigV4 signing
 * envelopes), and responds with the configured initial response and response events. Request assertions run after
 * the [EventStreamTestBuilder.operation] block completes.
 *
 * # Example
 * ```
 * fun fooTest() = eventStreamTest {
 *     expectedRequestEvent {
 *         headers = mapOf(":event-type" to HeaderValue.String("foo"))
 *     }
 *
 *     responseEvent("AAAAS...".decodeBase64Bytes())
 *
 *     operation { mockEngine ->
 *         val service = MyClient { httpClient = mockEngine }
 *         service.duplexOp(input) { resp -> resp.stream?.toList() }
 *     }
 * }
 * ```
 */
public fun eventStreamTest(block: EventStreamTestBuilder.() -> Unit): TestResult = runTest {
    val testBuilder = EventStreamTestBuilder().apply(block)

    var actualRequest: HttpRequest? = null
    var actualRequestBody: ByteArray? = null

    val mockEngine = TestEngine { _, request ->
        // Consuming the body drives the event stream serializer, so serialization failures surface from the
        // operation call
        val body = request.body.readAll()
        actualRequestBody = body
        actualRequest = HttpRequest(
            method = request.method,
            url = request.url,
            headers = request.headers,
            body = body?.let { HttpBody.fromBytes(it) } ?: HttpBody.Empty,
        )

        val initialResponse = testBuilder.initialResponse
        val headers = Headers {
            initialResponse.headers.forEach { (key, value) -> append(key, value) }
        }

        val responseBody = if (testBuilder.responseEvents.isNotEmpty()) {
            val buffer = SdkBuffer()
            testBuilder.responseEvents.forEach(buffer::write)
            HttpBody.fromBytes(buffer.readByteArray())
        } else {
            initialResponse.body?.let { HttpBody.fromBytes(it.encodeToByteArray()) } ?: HttpBody.Empty
        }

        val resp = HttpResponse(initialResponse.statusCode, headers, responseBody)
        val now = Instant.now()
        HttpCall(request, resp, now, now)
    }

    testBuilder.runOperation(mockEngine)

    testBuilder.expectedInitialRequest?.let { expected ->
        val actual = assertNotNull(actualRequest, "expected an initial request but none was sent")
        assertInitialRequest(expected.build(), actual)
    }

    if (testBuilder.expectedRequestEvents.isNotEmpty()) {
        val body = assertNotNull(actualRequestBody, "expected request events but no request body was sent")
        val actualEvents = decodeRequestEvents(body)
        assertEquals(
            testBuilder.expectedRequestEvents.size,
            actualEvents.size,
            "expected ${testBuilder.expectedRequestEvents.size} request events; got ${actualEvents.size}: $actualEvents",
        )
        testBuilder.expectedRequestEvents.zip(actualEvents).forEachIndexed { idx, (expected, actual) ->
            assertEventMessage(idx, expected, actual)
        }
    }
}

/**
 * Decode request event stream frames, unwrapping SigV4 signing envelopes and dropping the empty end frame.
 */
private suspend fun decodeRequestEvents(body: ByteArray): List<Message> = decodeFrames(SdkByteReadChannel(body)).toList().mapNotNull { frame ->
    when {
        frame.headers.none { it.name == CHUNK_SIGNATURE_HEADER } -> frame

        // the signed end frame has an empty payload
        frame.payload.isEmpty() -> null

        else -> Message.decode(SdkBuffer().apply { write(frame.payload) })
    }
}

private suspend fun assertInitialRequest(expected: ExpectedHttpRequest, actual: HttpRequest) {
    assertEquals(expected.method, actual.method, "expected method: `${expected.method}`; got: `${actual.method}`")
    assertEquals(expected.uri, actual.url.path.toString(), "expected path: `${expected.uri}`; got: `${actual.url.path}`")

    val actualParams = actual.url.parameters.encodedParameters
    expected.queryParams.forEach { (key, value) ->
        val actualValues = actualParams[key]
        assertNotNull(actualValues, "expected query parameter `$key`; no values found")
        assertTrue(value in actualValues, "query parameter `$key` does not contain expected value `$value`")
    }
    expected.forbiddenQueryParams.forEach { assertFalse(it in actualParams, "forbidden query parameter found: `$it`") }
    expected.requiredQueryParams.forEach { assertTrue(it in actualParams, "expected required query parameter not found: `$it`") }

    expected.headers.forEach { (name, value) ->
        val values = actual.headers.getAll(name)?.joinToString(separator = ", ")
        assertNotNull(values, "expected header `$name` has no actual values")
        assertEquals(value, values, "expected header name value pair not equal: `$name:$value`; found: `$name:$values`")
    }
    expected.forbiddenHeaders.forEach { assertFalse(actual.headers.contains(it), "forbidden header found: `$it`") }
    expected.requiredHeaders.forEach { assertTrue(actual.headers.contains(it), "expected required header not found: `$it`") }

    val expectedBody = expected.body?.let {
        assertNotNull(expected.bodyAssert, "body assertion function is required if an expected body is defined")
        HttpBody.fromBytes(it.encodeToByteArray())
    }
    expected.bodyAssert?.invoke(expectedBody, actual.body)
}

private fun assertEventMessage(idx: Int, expected: ExpectedEventMessage, actual: Message) {
    val actualHeaders = actual.headers.associate { it.name to it.value }

    expected.headers.forEach { (name, value) ->
        val actualValue = assertNotNull(actualHeaders[name], "event[$idx]: expected header `$name` not found")
        assertEquals(value, actualValue, "event[$idx]: header `$name` not equal")
    }
    expected.forbiddenHeaders.forEach {
        assertFalse(it in actualHeaders, "event[$idx]: forbidden header found: `$it`")
    }
    expected.requiredHeaders.forEach {
        assertTrue(it in actualHeaders, "event[$idx]: expected required header not found: `$it`")
    }

    val expectedBody = expected.body ?: return
    if (expected.bodyMediaType?.isJsonMediaType == true) {
        assertJsonStringsEqual(expectedBody, actual.payload.decodeToString())
    } else {
        assertContentEquals(expectedBody.encodeToByteArray(), actual.payload, "event[$idx]: payload not equal")
    }
}

private val String.isJsonMediaType: Boolean
    get() = this == "application/json" || endsWith("+json")

/**
 * An event stream message the client is expected to send
 */
public data class ExpectedEventMessage(
    // header names AND values that must appear
    public val headers: Map<String, HeaderValue> = mapOf(),
    // header names that must not appear
    public val forbiddenHeaders: List<String> = listOf(),
    // header names that must appear but no assertion on values
    public val requiredHeaders: List<String> = listOf(),
    // if no body is defined no assertions are made about it
    public val body: String? = null,
    // used to pick the body comparison (e.g. JSON vs exact bytes)
    public val bodyMediaType: String? = null,
)

public class ExpectedEventMessageBuilder {
    public var headers: Map<String, HeaderValue> = mapOf()
    public var forbiddenHeaders: List<String> = listOf()
    public var requiredHeaders: List<String> = listOf()
    public var body: String? = null
    public var bodyMediaType: String? = null

    public fun build(): ExpectedEventMessage = ExpectedEventMessage(headers, forbiddenHeaders, requiredHeaders, body, bodyMediaType)
}

/**
 * The initial HTTP response the mock engine returns. Event stream frames, if any, replace [body].
 */
public class InitialHttpResponseBuilder {
    public var statusCode: HttpStatusCode = HttpStatusCode.OK
    public var headers: Map<String, String> = mapOf()
    public var body: String? = null
}

/**
 * An event stream message the mock engine sends to the client
 */
public class ResponseEventBuilder {
    public var headers: Map<String, HeaderValue> = mapOf()
    public var body: ByteArray = ByteArray(0)
}

public class EventStreamTestBuilder {
    internal var runOperation: suspend (mockEngine: HttpClientEngine) -> Unit = {}
    internal var expectedInitialRequest: ExpectedHttpRequestBuilder? = null
    internal val expectedRequestEvents = mutableListOf<ExpectedEventMessage>()
    internal val initialResponse = InitialHttpResponseBuilder()
    internal val responseEvents = mutableListOf<ByteArray>()

    /**
     * Setup the expected initial HTTP request the service operation should produce
     */
    public fun expectedInitialRequest(block: ExpectedHttpRequestBuilder.() -> Unit) {
        expectedInitialRequest = (expectedInitialRequest ?: ExpectedHttpRequestBuilder()).apply(block)
    }

    /**
     * Add an event stream message the service operation is expected to send, in order
     */
    public fun expectedRequestEvent(block: ExpectedEventMessageBuilder.() -> Unit) {
        expectedRequestEvents.add(ExpectedEventMessageBuilder().apply(block).build())
    }

    /**
     * Setup the initial HTTP response the service operation receives
     */
    public fun initialResponse(block: InitialHttpResponseBuilder.() -> Unit) {
        initialResponse.apply(block)
    }

    /**
     * Add a fully encoded event stream message to send to the service operation
     */
    public fun responseEvent(bytes: ByteArray) {
        responseEvents.add(bytes)
    }

    /**
     * Add an event stream message, built from [ResponseEventBuilder.headers] and [ResponseEventBuilder.body],
     * to send to the service operation
     */
    public fun responseEvent(block: ResponseEventBuilder.() -> Unit) {
        val event = ResponseEventBuilder().apply(block)
        val message = Message(event.headers.map { (name, value) -> Header(name, value) }, event.body)
        val buffer = SdkBuffer()
        message.encode(buffer)
        responseEvents.add(buffer.readByteArray())
    }

    /**
     * Setup the service operation to run. The [HttpClientEngine] to use for the test is provided as input to the
     * function. The [block] is responsible for setting up the service client with the provided engine, providing the
     * input, executing the operation, and making assertions about the result.
     */
    public fun operation(block: suspend (mockEngine: HttpClientEngine) -> Unit) {
        runOperation = block
    }
}
