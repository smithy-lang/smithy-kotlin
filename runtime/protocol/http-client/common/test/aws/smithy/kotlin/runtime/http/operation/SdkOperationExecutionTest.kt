/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.auth.AnonymousIdentityProvider
import aws.smithy.kotlin.runtime.http.auth.AuthScheme
import aws.smithy.kotlin.runtime.http.auth.HttpSigner
import aws.smithy.kotlin.runtime.http.auth.SignHttpRequest
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.identity.asIdentityProviderConfig
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SdkOperationExecutionTest {
    @Test
    fun testAttemptTimeoutWithLongCall(): Unit = runBlocking {
        val serialized = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(serialized, Unit).apply {
            context[HttpOperationContext.AttemptTimeout] = 200.milliseconds
        }

        val engine = DelayingHttpEngine(listOf(300.milliseconds, 300.milliseconds, 100.milliseconds))
        val client = SdkHttpClient(engine)

        val result = op.roundTrip(client, Unit)
        assertEquals(Unit, result)
        assertEquals(3, engine.callCount)
    }

    @Test
    fun testAttemptTimeoutWithShortCall(): Unit = runBlocking {
        val serialized = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(serialized, Unit).apply {
            context[HttpOperationContext.AttemptTimeout] = 300.milliseconds
        }

        val engine = DelayingHttpEngine(200.milliseconds)
        val client = SdkHttpClient(engine)

        val result = op.roundTrip(client, Unit)
        assertEquals(Unit, result)
        assertEquals(1, engine.callCount)
    }

    @Test
    fun testCallTimeoutWithLongCall(): Unit = runBlocking {
        val serialized = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(serialized, Unit).apply {
            context[HttpOperationContext.CallTimeout] = 200.milliseconds
        }

        val engine = DelayingHttpEngine(300.milliseconds)
        val client = SdkHttpClient(engine)

        assertThrows<ClientTimeoutException> { op.roundTrip(client, Unit) }
        assertEquals(1, engine.callCount)
    }

    @Test
    fun testCallTimeoutWithShortCall(): Unit = runBlocking {
        val serialized = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(serialized, Unit).apply {
            context[HttpOperationContext.CallTimeout] = 300.milliseconds
        }

        val engine = DelayingHttpEngine(200.milliseconds)
        val client = SdkHttpClient(engine)

        val result = op.roundTrip(client, Unit)
        assertEquals(Unit, result)
        assertEquals(1, engine.callCount)
    }

    @Test
    fun testOperationMiddlewareOrder() = runTest {
        // sanity test middleware flows the way we expect
        val serialized = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(serialized, Unit)

        val mockEngine = object : HttpClientEngineBase("test engine") {
            override val config: HttpClientEngineConfig = HttpClientEngineConfig.Default

            private var attempt = 0
            override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
                attempt++
                if (attempt == 1) throw RetryableServiceTestException
                val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
                return HttpCall(request, resp, Instant.now(), Instant.now())
            }
        }

        val httpSigner = object : HttpSigner {
            override suspend fun sign(signingRequest: SignHttpRequest) {
                val request = signingRequest.httpRequest
                assertFalse(request.headers.contains("test-auth"))
                request.headers.append("test-auth", "test-signature")

                assertFalse(request.headers.contains("receive-header"))
            }
        }
        val authScheme = object : AuthScheme {
            override val schemeId: AuthSchemeId = AuthSchemeId.Anonymous
            override val signer: HttpSigner = httpSigner
        }

        op.execution.auth = OperationAuthConfig.from(
            AnonymousIdentityProvider.asIdentityProviderConfig(),
            authScheme,
        )

        val actualOrder = mutableListOf<String>()

        op.execution.initialize.intercept { req, next ->
            actualOrder.add("initialize")
            next.call(req)
        }

        op.execution.mutate.intercept { req, next ->
            actualOrder.add("mutate")
            req.subject.headers.append("mutate-header", "mutate")
            next.call(req)
        }

        op.execution.onEachAttempt.intercept { req, next ->
            actualOrder.add("attempt")
            // retry middleware should be giving us a fresh copy each attempt
            assertFalse(req.subject.headers.contains("attempt-header"))
            req.subject.headers.append("attempt-header", "per-attempt")

            // signing should come after this
            assertFalse(req.subject.headers.contains("test-auth"))
            next.call(req)
        }

        op.execution.receive.intercept { req, next ->
            actualOrder.add("receive")
            assertTrue(req.subject.headers.contains("attempt-header"))
            assertTrue(req.subject.headers.contains("test-auth"))
            assertTrue(req.subject.headers.contains("mutate-header"))

            assertFalse(req.subject.headers.contains("receive-header"))
            req.subject.headers.append("receive-header", "receive")
            next.call(req)
        }

        val client = SdkHttpClient(mockEngine)
        op.roundTrip(client, Unit)
        val expectedOrder = listOf("initialize", "mutate", "attempt", "receive", "attempt", "receive")
        assertEquals(expectedOrder, actualOrder)
    }
}

private class DelayingHttpEngine(durations: List<Duration>) : HttpClientEngineBase("test engine") {
    constructor(duration: Duration) : this(listOf(duration))

    private val durations = run {
        val terminator = durations.last()
        val initial = durations.dropLast(1)
        initial.asSequence() + generateSequence { terminator }
    }.iterator()

    var callCount = 0
        private set

    override val config: HttpClientEngineConfig = HttpClientEngineConfig.Default

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
        callCount++
        delay(durations.next())
        val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
        return HttpCall(request, resp, Instant.now(), Instant.now())
    }
}
