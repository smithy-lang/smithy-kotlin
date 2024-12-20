/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol

import aws.smithy.kotlin.runtime.SdkBaseException
import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.awsprotocol.ClockSkewInterceptor.Companion.CLOCK_SKEW_THRESHOLD
import aws.smithy.kotlin.runtime.awsprotocol.ClockSkewInterceptor.Companion.isSkewed
import aws.smithy.kotlin.runtime.client.ResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.until
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private val SKEWED_RESPONSE_CODE_DESCRIPTION = "RequestTimeTooSkewed"
private val POSSIBLE_SKEWED_RESPONSE_CODE_DESCRIPTION = "InvalidSignatureException"
private val NOT_SKEWED_RESPONSE_CODE_DESCRIPTION = "RequestThrottled"

class ClockSkewInterceptorTest {
    @Ignore // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testNotSkewed() {
        val clientTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        assertEquals(clientTime, serverTime)
        assertFalse(clientTime.isSkewed(serverTime, NOT_SKEWED_RESPONSE_CODE_DESCRIPTION))
    }

    @Ignore // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testSkewedByResponseCode() {
        // clocks are exactly the same, but service returned skew error
        val clientTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        assertTrue(clientTime.isSkewed(serverTime, SKEWED_RESPONSE_CODE_DESCRIPTION))
        assertEquals(0.days, clientTime.until(serverTime))
    }

    @Ignore // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testSkewedByTime() {
        val clientTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 7 Oct 2023 16:20:50 -0400")
        assertTrue(clientTime.isSkewed(serverTime, POSSIBLE_SKEWED_RESPONSE_CODE_DESCRIPTION))
        assertEquals(1.days, clientTime.until(serverTime))
    }

    @Ignore // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testNegativeSkewedByTime() {
        val clientTime = Instant.fromRfc5322("Wed, 7 Oct 2023 16:20:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        assertTrue(clientTime.isSkewed(serverTime, POSSIBLE_SKEWED_RESPONSE_CODE_DESCRIPTION))
        assertEquals(-1.days, clientTime.until(serverTime))
    }

    @Ignore // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testSkewThreshold() {
        val minute = 20
        var clientTime =
            Instant.fromRfc5322("Wed, 6 Oct 2023 16:${minute - CLOCK_SKEW_THRESHOLD.inWholeMinutes}:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:$minute:50 -0400")
        assertTrue(clientTime.isSkewed(serverTime, POSSIBLE_SKEWED_RESPONSE_CODE_DESCRIPTION))
        assertEquals(CLOCK_SKEW_THRESHOLD, clientTime.until(serverTime))

        // shrink the skew by one second, crossing the threshold
        clientTime += 1.seconds
        assertFalse(clientTime.isSkewed(serverTime, POSSIBLE_SKEWED_RESPONSE_CODE_DESCRIPTION))
    }

    private suspend fun testRoundTrip(
        serverTimeString: String,
        clientTimeString: String,
        httpStatusCode: HttpStatusCode,
        expectException: Boolean,
    ) {
        val serverTime = runCatching { Instant.fromRfc5322(serverTimeString) }.getOrNull()
        val clientTime = Instant.fromIso8601(clientTimeString)

        val client = getMockClient(
            "bla".encodeToByteArray(),
            Headers { append("Date", serverTimeString) },
            httpStatusCode,
        )

        val req = HttpRequestBuilder().apply {
            body = "<Foo>bar</Foo>".encodeToByteArray().toHttpBody()
        }
        req.headers.append("x-amz-date", clientTimeString)

        val op = newTestOperation<Unit, Unit>(req, Unit)

        val clockSkewException = SdkBaseException()
        clockSkewException.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] =
            POSSIBLE_SKEWED_RESPONSE_CODE_DESCRIPTION
        op.interceptors.add(FailedResultInterceptor(clockSkewException))

        op.interceptors.add(ClockSkewInterceptor())

        if (expectException) {
            assertFailsWith<SdkBaseException> {
                op.roundTrip(client, Unit)
            }

            // Validate no skew was detected
            assertNull(op.context.getOrNull(HttpOperationContext.ClockSkew))
        } else {
            op.roundTrip(client, Unit)

            serverTime?.let {
                // Validate the skew got stored in execution context
                val expectedSkew = clientTime.until(it)
                assertEquals(expectedSkew, op.context.getOrNull(HttpOperationContext.ClockSkew))
            }
        }
    }

    @Ignore // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testClockSkewApplied() = runTest {
        testRoundTrip(
            serverTimeString = "Wed, 14 Sep 2023 16:20:50 -0400", // Big skew
            clientTimeString = "20231006T131604Z",
            httpStatusCode = HttpStatusCode.Forbidden,
            expectException = false,
        )
    }

    @Ignore // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testClockSkewNotApplied_NoSkew() = runTest {
        testRoundTrip(
            serverTimeString = "Wed, 06 Oct 2023 13:16:04 -0000", // No skew
            clientTimeString = "20231006T131604Z",
            httpStatusCode = HttpStatusCode(403, POSSIBLE_SKEWED_RESPONSE_CODE_DESCRIPTION),
            expectException = true,
        )
    }

    @Ignore // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testClockSkewNotApplied_BadDate() = runTest {
        testRoundTrip(
            serverTimeString = "Wed, 06 Oct 23 13:16:04 -0000", // Two digit year == ☠️
            clientTimeString = "20231006T131604Z",
            httpStatusCode = HttpStatusCode(403, POSSIBLE_SKEWED_RESPONSE_CODE_DESCRIPTION),
            expectException = true,
        )
    }

    /**
     * An interceptor which returns a [Result.failure] with the given [exception] for the first [timesToFail] times its invoked.
     * This simulates a service returning a clock skew exception and then successfully processing any successive requests.
     */
    private class FailedResultInterceptor(val exception: SdkBaseException, val timesToFail: Int = 1) : HttpInterceptor {
        var failuresSent = 0

        override suspend fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
            if (failuresSent == timesToFail) {
                return context.response
            }
            failuresSent += 1
            return Result.failure(exception)
        }
    }

    private fun getMockClient(response: ByteArray, responseHeaders: Headers = Headers.Empty, httpStatusCode: HttpStatusCode = HttpStatusCode.OK): SdkHttpClient {
        val mockEngine = TestEngine { _, request ->
            val body = object : HttpBody.SourceContent() {
                override val contentLength: Long = response.size.toLong()
                override fun readFrom(): SdkSource = response.source()
                override val isOneShot: Boolean get() = false
            }
            val resp = HttpResponse(httpStatusCode, responseHeaders, body)
            HttpCall(request, resp, Instant.now(), Instant.now())
        }
        return SdkHttpClient(mockEngine)
    }

    /**
     * Create a new test operation using [serialized] as the already serialized version of the input type [I]
     * and [deserialized] as the result of "deserialization" from an HTTP response.
     */
    inline fun <reified I, reified O> newTestOperation(serialized: HttpRequestBuilder, deserialized: O): SdkHttpOperation<I, O> =
        SdkHttpOperation.build<I, O> {
            serializeWith = object : HttpSerializer.NonStreaming<I> {
                override fun serialize(context: ExecutionContext, input: I): HttpRequestBuilder = serialized
            }
            deserializeWith = object : HttpDeserializer.NonStreaming<O> {
                override fun deserialize(context: ExecutionContext, call: HttpCall, payload: ByteArray?): O = deserialized
            }

            // required operation context
            operationName = "TestOperation"
            serviceName = "TestService"
        }
}
