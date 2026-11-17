/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.awsprotocol.eventstream.HeaderValue
import aws.smithy.kotlin.runtime.awsprotocol.eventstream.Message
import aws.smithy.kotlin.runtime.awsprotocol.eventstream.buildMessage
import aws.smithy.kotlin.runtime.awsprotocol.eventstream.decodeFrames
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class EventStreamTestBuilderTest {
    private fun Message.encodeToByteArray(): ByteArray = SdkBuffer().also { encode(it) }.readByteArray()

    private val fooEvent = buildMessage {
        addHeader(":message-type", HeaderValue.String("event"))
        addHeader(":event-type", HeaderValue.String("foo"))
        addHeader(":content-type", HeaderValue.String("application/json"))
        payload = """{"bar":"baz"}""".encodeToByteArray()
    }

    // emulates a SigV4-signed event followed by the signed empty end frame
    private fun signed(inner: ByteArray): ByteArray = buildMessage {
        addHeader(":chunk-signature", HeaderValue.ByteArray(byteArrayOf(1, 2, 3)))
        payload = inner
    }.encodeToByteArray()

    private fun requestWithBody(body: ByteArray) = HttpRequestBuilder().apply {
        method = HttpMethod.POST
        url { path.encoded = "/foo" }
        headers.append("x-foo", "bar")
        this.body = HttpBody.fromBytes(body)
    }.build()

    @Test
    fun itAssertsSignedRequestEvents() = eventStreamTest {
        expectedInitialRequest {
            method = HttpMethod.POST
            uri = "/foo"
            headers = mapOf("x-foo" to "bar")
        }

        expectedRequestEvent {
            headers = mapOf(":event-type" to HeaderValue.String("foo"))
            requiredHeaders = listOf(":message-type")
            forbiddenHeaders = listOf(":chunk-signature")
            body = """{ "bar": "baz" }"""
            bodyMediaType = "application/json"
        }

        operation { mockEngine ->
            val body = signed(fooEvent.encodeToByteArray()) + signed(ByteArray(0))
            mockEngine.roundTrip(ExecutionContext(), requestWithBody(body))
        }
    }

    @Test
    fun itAssertsUnsignedRequestEvents() = eventStreamTest {
        expectedRequestEvent {
            headers = mapOf(":event-type" to HeaderValue.String("foo"))
        }

        operation { mockEngine ->
            mockEngine.roundTrip(ExecutionContext(), requestWithBody(fooEvent.encodeToByteArray()))
        }
    }

    @Test
    fun itFailsOnMismatchedRequestEvent() {
        assertFailsWith<AssertionError> {
            eventStreamTest {
                expectedRequestEvent {
                    headers = mapOf(":event-type" to HeaderValue.String("bar"))
                }

                operation { mockEngine ->
                    mockEngine.roundTrip(ExecutionContext(), requestWithBody(fooEvent.encodeToByteArray()))
                }
            }
        }
    }

    @Test
    fun itSendsResponseEvents() = eventStreamTest {
        initialResponse {
            statusCode = HttpStatusCode.Accepted
            headers = mapOf("x-foo" to "bar")
        }
        responseEvent(fooEvent.encodeToByteArray())
        responseEvent {
            headers = mapOf(":event-type" to HeaderValue.String("qux"))
            body = "quux".encodeToByteArray()
        }

        operation { mockEngine ->
            val call = mockEngine.roundTrip(ExecutionContext(), requestWithBody(ByteArray(0)))
            assertEquals(HttpStatusCode.Accepted, call.response.status)
            assertEquals("bar", call.response.headers["x-foo"])

            val body = call.response.body.readAll()!!
            val messages = decodeFrames(SdkByteReadChannel(body)).toList()
            assertEquals(2, messages.size)
            assertEquals(fooEvent, messages[0])
            assertEquals("quux", messages[1].payload.decodeToString())
        }
    }

    @Test
    fun itSendsInitialResponseBodyWithoutEvents() = eventStreamTest {
        initialResponse {
            statusCode = HttpStatusCode.InternalServerError
            body = "service unavailable"
        }

        operation { mockEngine ->
            val call = mockEngine.roundTrip(ExecutionContext(), requestWithBody(ByteArray(0)))
            assertEquals(HttpStatusCode.InternalServerError, call.response.status)
            assertEquals("service unavailable", call.response.body.readAll()?.decodeToString())
        }
    }

    // runs the harness against a request body and asserts the harness itself rejects the expectations
    private fun assertRejects(
        requestBody: ByteArray,
        expectations: EventStreamTestBuilder.() -> Unit,
    ) {
        assertFailsWith<AssertionError> {
            eventStreamTest {
                expectations()
                operation { mockEngine ->
                    mockEngine.roundTrip(ExecutionContext(), requestWithBody(requestBody))
                }
            }
        }
    }

    private val signedFooStream get() = signed(fooEvent.encodeToByteArray()) + signed(ByteArray(0))

    @Test
    fun itRejectsFewerRequestEventsThanExpected() = assertRejects(ByteArray(0)) {
        expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
    }

    @Test
    fun itRejectsMoreRequestEventsThanExpected() = assertRejects(fooEvent.encodeToByteArray() + fooEvent.encodeToByteArray()) {
        expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
    }

    @Test
    fun itRejectsRequestEventsInTheWrongOrder() {
        val barEvent = buildMessage { addHeader(":event-type", HeaderValue.String("bar")) }
        assertRejects(fooEvent.encodeToByteArray() + barEvent.encodeToByteArray()) {
            expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("bar")) }
            expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
        }
    }

    @Test
    fun itRejectsForbiddenAndMissingRequiredHeaders() {
        assertRejects(fooEvent.encodeToByteArray()) {
            expectedRequestEvent { forbiddenHeaders = listOf(":event-type") }
        }
        assertRejects(fooEvent.encodeToByteArray()) {
            expectedRequestEvent { requiredHeaders = listOf("x-missing") }
        }
    }

    @Test
    fun itRejectsBodyMismatches() {
        assertRejects(fooEvent.encodeToByteArray()) {
            expectedRequestEvent {
                body = """{"bar":"qux"}"""
                bodyMediaType = "application/json"
            }
        }
        assertRejects(fooEvent.encodeToByteArray()) {
            expectedRequestEvent { body = "not the payload" }
        }
        assertRejects(
            buildMessage { payload = byteArrayOf(0xa1.toByte(), 0x61, 0x61, 0x01) }.encodeToByteArray(),
        ) {
            expectedRequestEvent {
                // {"a": 2}
                body = byteArrayOf(0xa1.toByte(), 0x61, 0x61, 0x02).encodeBase64().decodeToString()
                bodyMediaType = "application/cbor"
            }
        }
    }

    @Test
    fun itAcceptsEquivalentCborBodies() = eventStreamTest {
        expectedRequestEvent {
            // {"a": 1} definite length
            body = byteArrayOf(0xa1.toByte(), 0x61, 0x61, 0x01).encodeBase64().decodeToString()
            bodyMediaType = "application/cbor"
        }
        operation { mockEngine ->
            // {"a": 1} indefinite length
            val event = buildMessage { payload = byteArrayOf(0xbf.toByte(), 0x61, 0x61, 0x01, 0xff.toByte()) }
            mockEngine.roundTrip(ExecutionContext(), requestWithBody(event.encodeToByteArray()))
        }
    }

    @Test
    fun itRejectsInitialRequestMismatches() {
        assertRejects(ByteArray(0)) {
            expectedInitialRequest { uri = "/other" }
        }
        assertRejects(ByteArray(0)) {
            expectedInitialRequest {
                uri = "/foo"
                headers = mapOf("x-foo" to "other")
            }
        }
        assertRejects(ByteArray(0)) {
            expectedInitialRequest {
                uri = "/foo"
                forbiddenHeaders = listOf("x-foo")
            }
        }
    }

    @Test
    fun itRejectsDuplicateEventHeaders() {
        val duplicated = buildMessage {
            addHeader(":event-type", HeaderValue.String("foo"))
            addHeader(":event-type", HeaderValue.String("foo"))
        }
        assertRejects(duplicated.encodeToByteArray()) {
            expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
        }
    }

    @Test
    fun itAcceptsSignedEventsWhenSignedIsExpected() = eventStreamTest {
        expectSignedRequestEvents(true)
        expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
        operation { mockEngine -> mockEngine.roundTrip(ExecutionContext(), requestWithBody(signedFooStream)) }
    }

    @Test
    fun itAcceptsUnsignedEventsWhenUnsignedIsExpected() = eventStreamTest {
        expectSignedRequestEvents(false)
        expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
        operation { mockEngine -> mockEngine.roundTrip(ExecutionContext(), requestWithBody(fooEvent.encodeToByteArray())) }
    }

    @Test
    fun itRejectsUnsignedEventsWhenSignedIsExpected() = assertRejects(fooEvent.encodeToByteArray()) {
        expectSignedRequestEvents(true)
        expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
    }

    @Test
    fun itRejectsSignedEventsWhenUnsignedIsExpected() = assertRejects(signedFooStream) {
        expectSignedRequestEvents(false)
        expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
    }

    @Test
    fun itRejectsSignedStreamsWithoutAnEndFrame() = assertRejects(signed(fooEvent.encodeToByteArray())) {
        expectSignedRequestEvents(true)
        expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
    }

    @Test
    fun itRejectsSignedStreamsWithAnEarlyEndFrame() = assertRejects(signed(ByteArray(0)) + signed(fooEvent.encodeToByteArray())) {
        expectSignedRequestEvents(true)
        expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
    }

    @Test
    fun itRejectsSignedFramesWithTrailingBytesInTheEnvelope() {
        val envelope = signed(fooEvent.encodeToByteArray() + byteArrayOf(1, 2, 3)) + signed(ByteArray(0))
        assertRejects(envelope) {
            expectSignedRequestEvents(true)
            expectedRequestEvent { headers = mapOf(":event-type" to HeaderValue.String("foo")) }
        }
    }

    @Test
    fun itSendsAnEmptyEventStreamWhenNoResponseEventsAreConfigured() = eventStreamTest {
        operation { mockEngine ->
            val call = mockEngine.roundTrip(ExecutionContext(), requestWithBody(ByteArray(0)))
            val body = assertNotNull(call.response.body.readAll(), "expected a present response body")
            assertEquals(0, body.size)
            assertEquals(0, decodeFrames(SdkByteReadChannel(body)).toList().size)
        }
    }

    @Test
    fun itPrefersResponseEventsOverTheInitialResponseBody() = eventStreamTest {
        initialResponse { body = "ignored" }
        responseEvent(fooEvent.encodeToByteArray())

        operation { mockEngine ->
            val call = mockEngine.roundTrip(ExecutionContext(), requestWithBody(ByteArray(0)))
            val messages = decodeFrames(SdkByteReadChannel(call.response.body.readAll()!!)).toList()
            assertEquals(listOf(fooEvent), messages)
        }
    }
}
