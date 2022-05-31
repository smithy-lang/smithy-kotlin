/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.operation.SdkHttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Base tests applicable for all client engines
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpClientEngineTest {

    class MockEngine : HttpClientEngineBase("test") {
        var shutdownCalled = false

        // store a reference for usage in cancellation tests
        // lateinit var callJob: Job

        val inFlightJobs: List<Job>
            get() = coroutineContext.job.children.toList()

        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
            val callContext = callContext()
            // callJob = callContext.job
            val response = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
            return HttpCall(request, response, Instant.now(), Instant.now(), callContext)
        }

        override fun shutdown() {
            shutdownCalled = true
        }
    }

    private val engine = MockEngine()
    private val client = sdkHttpClient(engine, manageEngine = true)

    private val HttpCall.job: Job
        get() = callContext.job

    @Test
    fun testCallComplete() = runTest {
        val call = client.call(SdkHttpRequest(HttpRequestBuilder()))
        assertTrue(call.job.isActive)
        call.complete()
        assertFalse(call.job.isActive)
        assertTrue(call.job.isCompleted)
    }

    @Test
    fun testUserContextCancelsRequestJob() = runTest {
        val job = launch {
            client.call(SdkHttpRequest(HttpRequestBuilder()))
            delay(1000)
        }
        yield()
        val callJob = engine.inFlightJobs.first()
        assertFalse(callJob.isCancelled)
        job.cancel()
        assertTrue(callJob.isCancelled)
    }

    @Test
    fun testInFlightRequestJobsAreIndependent() = runTest {
        val job1 = launch {
            client.call(SdkHttpRequest(HttpRequestBuilder()))
            delay(1000)
        }
        yield()

        val job2 = launch {
            client.call(SdkHttpRequest(HttpRequestBuilder()))
            delay(1000)
        }
        yield()
        val inflight = engine.inFlightJobs
        assertEquals(2, inflight.size)
        assertTrue(inflight.all { it.isActive })
        job1.cancel()

        assertTrue(inflight.first().isCancelled)
        assertFalse(inflight.last().isCancelled)
        assertTrue(inflight.last().isActive)
        job2.cancel()
    }

    @Test
    fun testEngineJobNotCancelledByRequestJobs() = runTest {
        launch {
            client.call(SdkHttpRequest(HttpRequestBuilder()))
            delay(1000)
        }
        yield()

        launch {
            client.call(SdkHttpRequest(HttpRequestBuilder()))
            delay(1000)
        }
        yield()
        engine.inFlightJobs.forEach { it.cancel() }
        assertTrue(engine.coroutineContext.job.isActive)
    }

    @Test
    fun testShutdownOnlyAfterInFlightDone() = runTest {
        val waiter = Channel<Unit>(1)
        launch {
            val call = client.call(SdkHttpRequest(HttpRequestBuilder()))
            waiter.receive()
            call.complete()
        }
        yield()
        launch {
            val call = client.call(SdkHttpRequest(HttpRequestBuilder()))
            waiter.receive()
            call.complete()
        }
        yield()

        assertEquals(2, engine.inFlightJobs.size)
        assertTrue(engine.inFlightJobs.all { it.isActive })
        client.close()
        assertTrue(engine.coroutineContext.job.isActive)
        assertFalse(engine.shutdownCalled)

        waiter.send(Unit)
        yield()
        assertTrue(engine.coroutineContext.job.isActive)
        assertFalse(engine.shutdownCalled)
        waiter.send(Unit)
        yield()

        assertFalse(engine.coroutineContext.job.isActive)
        assertTrue(engine.shutdownCalled)
    }

    @Test
    fun testRequestAfterClose() = runTest {
        client.close()
        assertFailsWith(HttpClientEngineClosedException::class) {
            client.call(SdkHttpRequest(HttpRequestBuilder()))
        }
        Unit
    }

    @Test
    fun testCloseUnmanagedEngine() = runTest {
        val client = sdkHttpClient(engine, manageEngine = false)
        client.close()
        assertFalse(engine.coroutineContext.job.isCompleted)
    }
}
