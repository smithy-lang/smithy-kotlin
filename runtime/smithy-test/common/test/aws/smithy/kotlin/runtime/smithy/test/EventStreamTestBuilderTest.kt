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
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
