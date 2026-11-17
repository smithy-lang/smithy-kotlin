/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol

import aws.smithy.kotlin.runtime.SdkBaseException
import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
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
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.delay.DelayProvider
import aws.smithy.kotlin.runtime.retries.policy.StandardRetryPolicy
import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.time.until
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ClockSkewTooGreatException : SdkBaseException()

/**
 * Integration tests for clock skew detection and correction.
 *
 * Uses a mock HTTP engine that simulates a skew-aware server: it checks the signing timestamp on each request and
 * returns `RequestTimeTooSkewed` if the request time is more than 4 minutes from the server's time, otherwise 200. The
 * server always returns its time in the `Date` header.
 */
class ClockSkewIntegrationTest {
    private val clientTime = Instant.fromEpochSeconds(1000)
    private val testClock = object : Clock {
        override fun now(): Instant = clientTime
    }

    /**
     * Simulates signing by computing the effective signing time (client clock + skew) and sharing it with the mock
     * engine via [SkewServer.lastSigningTime]. Also stamps `x-amz-date` on the request so the mock engine can validate
     * it.
     */
    private class MockSigningInterceptor(
        private val clock: Clock,
        private val server: SkewServer,
    ) : HttpInterceptor {
        override suspend fun modifyBeforeSigning(
            context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
        ): HttpRequest {
            val skew = context.executionContext.getOrNull(HttpOperationContext.ClockSkew) ?: Duration.ZERO
            server.lastSigningTime = clock.now() + skew
            return context.protocolRequest
        }
    }

    /**
     * Inspects the HTTP response and, if it's an error, returns a [Result.failure] with the appropriate error code.
     * Simulates what the real deserialization/error-handling middleware does.
     */
    private class MockErrorInterceptor : HttpInterceptor {
        override suspend fun modifyBeforeAttemptCompletion(
            context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>,
        ): Result<Any> {
            val response = context.protocolResponse ?: return context.response
            if (response.status.value in 200..299) return context.response
            val ex = ClockSkewTooGreatException()
            ex.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] = "RequestTimeTooSkewed"
            return Result.failure(ex)
        }
    }

    private class SkewServer(var serverTime: Instant) {
        var attempts = 0
            private set
        var lastSigningTime: Instant = Instant.fromEpochSeconds(0)

        fun reset() {
            attempts = 0
        }

        fun buildClient(): SdkHttpClient {
            val server = this
            val engine = TestEngine { _, request ->
                server.attempts++
                val reqTime = server.lastSigningTime
                val diff = reqTime.until(server.serverTime)

                val dateHeader = server.serverTime.format(TimestampFormat.RFC_5322)
                val (status, body) = if (diff.absoluteValue > 4.minutes) {
                    HttpStatusCode(403, "Forbidden") to """{"__type":"RequestTimeTooSkewed","message":"skewed"}"""
                } else {
                    HttpStatusCode.OK to "{}"
                }

                val responseBody = body.encodeToByteArray()
                val httpBody = object : HttpBody.SourceContent() {
                    override val contentLength: Long = responseBody.size.toLong()
                    override fun readFrom(): SdkSource = responseBody.source()
                    override val isOneShot: Boolean get() = false
                }
                val resp = HttpResponse(status, Headers { append("Date", dateHeader) }, httpBody)
                HttpCall(request, resp, Instant.now(), Instant.now())
            }
            return SdkHttpClient(engine)
        }
    }

    private fun buildOp(
        clockSkewInterceptor: ClockSkewInterceptor,
        server: SkewServer,
        maxAttempts: Int = 3,
    ): SdkHttpOperation<Unit, Unit> {
        val op = SdkHttpOperation.build<Unit, Unit> {
            serializeWith = object : HttpSerializer.NonStreaming<Unit> {
                override fun serialize(
                    context: ExecutionContext,
                    input: Unit,
                ): HttpRequestBuilder = HttpRequestBuilder()
            }
            deserializeWith = object : HttpDeserializer.NonStreaming<Unit> {
                override fun deserialize(context: ExecutionContext, call: HttpCall, payload: ByteArray?): Unit = Unit
            }
            operationName = "TestOperation"
            serviceName = "TestService"
        }

        op.execution.retryStrategy = StandardRetryStrategy {
            this.maxAttempts = maxAttempts
            delayProvider = object : DelayProvider {
                override val config = object : DelayProvider.Config {
                    override fun toBuilderApplicator(): DelayProvider.Config.Builder.() -> Unit = {}
                }
                override suspend fun backoff(attempt: Int) {}
            }
        }
        op.execution.retryPolicy = StandardRetryPolicy.Default

        // Order matters: MockErrorInterceptor must run before ClockSkewInterceptor in modifyBeforeAttemptCompletion so
        // the skew interceptor sees the error result. ClockSkewInterceptor must run before MockSigningInterceptor in
        // modifyBeforeSigning so the skew is applied to context before the mock signer captures the signing time.
        op.interceptors.add(MockErrorInterceptor())
        op.interceptors.add(clockSkewInterceptor)
        op.interceptors.add(MockSigningInterceptor(testClock, server))

        return op
    }

    /**
     * Test 1: Client time and server time are the same. A single request succeeds on the first attempt with no
     * correction.
     */
    @Test
    fun testNoSkew() = runTest {
        val server = SkewServer(clientTime)
        val client = server.buildClient()
        val interceptor = ClockSkewInterceptor(testClock)

        buildOp(interceptor, server).roundTrip(client, Unit)
        assertEquals(1, server.attempts)
    }

    /**
     * Test 2: Server is offset by -5 minutes from the client. Retries enabled (`maxAttempts = 3`). Consists of 4 calls:
     * calls:
     *
     * 1. 2 attempts. First attempt is skewed, retry with corrected offset succeeds.
     * 2. 1 attempt. Stored offset from op 1 is applied, first attempt succeeds.
     * 3. 2 attempts. Server clock fixed, stale offset causes skew, retry heals.
     * 4. 1 attempt. Healed offset sticks, first attempt succeeds.
     */
    @Test
    fun testSkewCorrectionWithRetries() = runTest {
        val server = SkewServer(clientTime - 5.minutes)
        val client = server.buildClient()
        val interceptor = ClockSkewInterceptor(testClock)

        // Op 1: skew triggers retry, corrects on attempt 2
        buildOp(interceptor, server).roundTrip(client, Unit)
        assertEquals(2, server.attempts)

        // Op 2: stored offset lets first attempt succeed
        server.reset()
        buildOp(interceptor, server).roundTrip(client, Unit)
        assertEquals(1, server.attempts)

        // Op 3: server clock fixed, stale offset causes a retry then heals
        server.serverTime = clientTime
        server.reset()
        buildOp(interceptor, server).roundTrip(client, Unit)
        assertEquals(2, server.attempts)

        // Op 4: healed offset sticks
        server.reset()
        buildOp(interceptor, server).roundTrip(client, Unit)
        assertEquals(1, server.attempts)
    }

    /**
     * Test 3: Same as test 2 but with no retries (`maxAttempts = 1`). The offset should still be saved/healed even when
     * the operation itself fails. Consists of 4 calls:
     *
     * 1. Error. Skewed, no retry budget, but offset is saved.
     * 2. Success. Saved offset applied, first attempt succeeds.
     * 3. Error. Stale offset causes skew, no retry budget, but offset is healed.
     * 4. Success. Healed offset sticks, first attempt succeeds.
     */
    @Test
    fun testSkewCorrectionWithoutRetries() = runTest {
        val server = SkewServer(clientTime - 5.minutes)
        val client = server.buildClient()
        val interceptor = ClockSkewInterceptor(testClock)

        // Op 1: skew detected, no retry budget, but offset is saved
        assertFailsWith<ClockSkewTooGreatException> {
            buildOp(interceptor, server, maxAttempts = 1).roundTrip(client, Unit)
        }
        assertEquals(1, server.attempts)

        // Op 2: saved offset applied, succeeds
        server.reset()
        buildOp(interceptor, server, maxAttempts = 1).roundTrip(client, Unit)
        assertEquals(1, server.attempts)

        // Op 3: server clock fixed, stale offset causes skew, fails but heals
        server.serverTime = clientTime
        server.reset()
        assertFailsWith<ClockSkewTooGreatException> {
            buildOp(interceptor, server, maxAttempts = 1).roundTrip(client, Unit)
        }
        assertEquals(1, server.attempts)

        // Op 4: healed offset sticks
        server.reset()
        buildOp(interceptor, server, maxAttempts = 1).roundTrip(client, Unit)
        assertEquals(1, server.attempts)
    }
}
