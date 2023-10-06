/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.interceptors.ClockSkewInterceptor.Companion.CLOCK_SKEW_THRESHOLD
import aws.smithy.kotlin.runtime.http.interceptors.ClockSkewInterceptor.Companion.getSkew
import aws.smithy.kotlin.runtime.http.interceptors.ClockSkewInterceptor.Companion.isSkewed
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class ClockSkewInterceptorTest {
    @Test
    fun testNotSkewed() {
        val clientTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        assertFalse(clientTime.isSkewed(serverTime))
    }

    @Test
    fun testSkewed() {
        val clientTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 7 Oct 2023 16:20:50 -0400")
        assertTrue(clientTime.isSkewed(serverTime))
        assertEquals(1.days, clientTime.getSkew(serverTime))
    }

    @Test
    fun testNegativeSkewed() {
        val clientTime = Instant.fromRfc5322("Wed, 7 Oct 2023 16:20:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:20:50 -0400")
        assertTrue(clientTime.isSkewed(serverTime))
        assertEquals(Duration.ZERO - 1.days, clientTime.getSkew(serverTime))
    }

    @Test
    fun testSkewThreshold() {
        val minute = 20
        var clientTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:${minute - CLOCK_SKEW_THRESHOLD.inWholeMinutes}:50 -0400")
        val serverTime = Instant.fromRfc5322("Wed, 6 Oct 2023 16:$minute:50 -0400")
        assertTrue(clientTime.isSkewed(serverTime))
        assertEquals(CLOCK_SKEW_THRESHOLD, clientTime.getSkew(serverTime))

        // narrow the skew by one second, crossing the threshold
        clientTime += 1.seconds
        assertFalse(clientTime.isSkewed(serverTime))
    }

    @Test
    fun testClockSkewApplied() = runTest {
        val serverTimeString = "Wed, 14 Sep 2023 16:20:50 -0400"
        val serverTime = Instant.fromRfc5322(serverTimeString)

        val clientTimeString = "20231006T131604Z"
        val clientTime = Instant.fromIso8601(clientTimeString)

        val client = getMockClient("bla".encodeToByteArray(), Headers {
            append("Date", serverTimeString)
        })

        val req = HttpRequestBuilder().apply {
            body = ByteArrayContent("<Foo>bar</Foo>".encodeToByteArray())
        }
        req.headers.append("x-amz-date", clientTimeString)

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(ClockSkewInterceptor())

        op.roundTrip(client, Unit)

        // Validate the skew got stored in execution context
        val expectedSkew = clientTime.getSkew(serverTime)
        assertEquals(op.context.getOrNull(HttpOperationContext.ClockSkew), expectedSkew)
    }

    @Test
    fun testClockSkewNotApplied() = runTest {
        val serverTimeString = "Wed, 06 Oct 2023 13:16:04 -0000"
        val clientTimeString = "20231006T131604Z"

        val client = getMockClient("bla".encodeToByteArray(), Headers {
            append("Date", serverTimeString)
        })

        val req = HttpRequestBuilder().apply {
            body = ByteArrayContent("<Foo>bar</Foo>".encodeToByteArray())
        }
        req.headers.append("x-amz-date", clientTimeString)

        val op = newTestOperation<Unit, Unit>(req, Unit)
        op.interceptors.add(ClockSkewInterceptor())

        op.roundTrip(client, Unit)

        // Validate no skew was detected
        assertNull(op.context.getOrNull(HttpOperationContext.ClockSkew))
    }

    private fun getMockClient(response: ByteArray, responseHeaders: Headers = Headers.Empty): SdkHttpClient {
        val mockEngine = TestEngine { _, request ->
            val body = object : HttpBody.SourceContent() {
                override val contentLength: Long = response.size.toLong()
                override fun readFrom(): SdkSource = response.source()
                override val isOneShot: Boolean get() = false
            }
            val resp = HttpResponse(HttpStatusCode.OK, responseHeaders, body)
            HttpCall(request, resp, Instant.now(), Instant.now())
        }
        return SdkHttpClient(mockEngine)
    }
}
