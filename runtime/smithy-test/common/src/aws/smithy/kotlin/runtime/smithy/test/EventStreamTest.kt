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
import aws.smithy.kotlin.runtime.http.HeadersBuilder
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
import aws.smithy.kotlin.runtime.text.encoding.decodeBase64Bytes
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
 * The request stream must complete without waiting on response events because the request body is read in full
 * before the response is returned.
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
        val contentLength = request.body.contentLength
        val body = request.body.readAll()
        actualRequestBody = body

        // engines derive Content-Length from the body, serializers do not set it
        val actualHeaders = HeadersBuilder().apply {
            appendAll(request.headers)
            if (contentLength != null && contentLength > 0) set("Content-Length", contentLength.toString())
        }.build()
        actualRequest = HttpRequest(
            method = request.method,
            url = request.url,
            headers = actualHeaders,
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
            // a present but empty body is an event stream with no events, as opposed to no response body
            HttpBody.fromBytes(initialResponse.body?.encodeToByteArray() ?: ByteArray(0))
        }

        val resp = HttpResponse(initialResponse.statusCode, headers, responseBody)
        val now = Instant.now()
        HttpCall(request, resp, now, now)
    }

    testBuilder.runOperation(mockEngine)

    testBuilder.expectedInitialRequest?.let { expected ->
        val actual = assertNotNull(actualRequest, "expected an initial request but none was sent")
        assertRequest(expected.build(), actual)
    }

    if (testBuilder.expectedRequestEvents.isNotEmpty()) {
        val body = assertNotNull(actualRequestBody, "expected request events but no request body was sent")
        val actualEvents = decodeRequestEvents(body, testBuilder.expectSigned)
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
 * Decode request event stream frames.
 *
 * When [expectSigned] is true every frame must be a SigV4 signing envelope wrapping exactly one message, followed by
 * a single empty signed end frame as the last frame. When false no frame may carry a signature. When null, signed
 * and unsigned frames are both accepted and the empty end frame is dropped wherever it appears.
 */
private suspend fun decodeRequestEvents(body: ByteArray, expectSigned: Boolean?): List<Message> {
    val frames = decodeFrames(SdkByteReadChannel(body)).toList()
    val isEnvelope = { frame: Message -> frame.headers.any { it.name == CHUNK_SIGNATURE_HEADER } }

    when (expectSigned) {
        true -> {
            frames.forEachIndexed { idx, frame ->
                assertTrue(isEnvelope(frame), "frame[$idx]: expected a signed frame but `$CHUNK_SIGNATURE_HEADER` is missing")
            }
            assertTrue(frames.isNotEmpty() && frames.last().payload.isEmpty(), "expected the last frame to be the empty signed end frame")
            assertEquals(1, frames.count { it.payload.isEmpty() }, "expected exactly one empty signed end frame")
        }
        false -> frames.forEachIndexed { idx, frame ->
            assertFalse(isEnvelope(frame), "frame[$idx]: expected an unsigned frame but found `$CHUNK_SIGNATURE_HEADER`")
        }
        null -> Unit
    }

    return frames.mapNotNull { frame ->
        when {
            !isEnvelope(frame) -> frame

            // the signed end frame has an empty payload
            frame.payload.isEmpty() -> null

            else -> {
                val inner = SdkBuffer().apply { write(frame.payload) }
                Message.decode(inner).also { assertTrue(inner.exhausted(), "signed frame has trailing bytes after the wrapped message") }
            }
        }
    }
}

private fun assertEventMessage(idx: Int, expected: ExpectedEventMessage, actual: Message) {
    val actualHeaderNames = actual.headers.map { it.name }
    assertEquals(actualHeaderNames.distinct(), actualHeaderNames, "event[$idx]: duplicate header names: $actualHeaderNames")
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
    when {
        expected.bodyMediaType?.isJsonMediaType == true ->
            assertJsonStringsEqual(expectedBody, actual.payload.decodeToString())
        // binary test bodies are base64-encoded
        expected.bodyMediaType?.isBinaryMediaType == true ->
            assertCborEqual(expectedBody.decodeBase64Bytes(), actual.payload, "event[$idx]: CBOR payload not equal")
        else ->
            assertContentEquals(expectedBody.encodeToByteArray(), actual.payload, "event[$idx]: payload not equal")
    }
}

private val String.isJsonMediaType: Boolean
    get() = lowercase().let { it == "application/json" || it.endsWith("+json") }

/**
 * An event stream message the client is expected to send
 */
internal data class ExpectedEventMessage(
    // header names AND values that must appear
    val headers: Map<String, HeaderValue> = mapOf(),
    // header names that must not appear
    val forbiddenHeaders: List<String> = listOf(),
    // header names that must appear but no assertion on values
    val requiredHeaders: List<String> = listOf(),
    // if no body is defined no assertions are made about it
    val body: String? = null,
    // used to pick the body comparison (e.g. JSON vs exact bytes)
    val bodyMediaType: String? = null,
)

public class ExpectedEventMessageBuilder {
    public var headers: Map<String, HeaderValue> = mapOf()
    public var forbiddenHeaders: List<String> = listOf()
    public var requiredHeaders: List<String> = listOf()
    public var body: String? = null
    public var bodyMediaType: String? = null

    internal fun build(): ExpectedEventMessage = ExpectedEventMessage(headers, forbiddenHeaders, requiredHeaders, body, bodyMediaType)
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
    internal var expectSigned: Boolean? = null
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
     * Require the request events to be SigV4 signed ([signed] true) or unsigned ([signed] false). Without this call
     * both are accepted.
     */
    public fun expectSignedRequestEvents(signed: Boolean) {
        expectSigned = signed
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
