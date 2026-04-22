/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.http.HeadersBuilder
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RetryMiddlewareTest {
    private val client = SdkHttpClient(TestEngine())

    private val policy = object : RetryPolicy<Any?> {
        var attempts = 0
        override fun evaluate(result: Result<Any?>): RetryDirective = if (attempts < 1) {
            attempts++
            RetryDirective.RetryError(RetryErrorType.ServerSide)
        } else {
            RetryDirective.TerminateAndSucceed
        }
    }

    @Test
    fun testRetryMiddleware() = runTest {
        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)
        val strategy = StandardRetryStrategy()

        op.execution.retryStrategy = strategy
        op.execution.retryPolicy = policy

        op.roundTrip(client, Unit)
        val attempts = op.context.attributes[HttpOperationContext.HttpCallList].size
        assertEquals(2, attempts)
    }

    /**
     * SEP 2.1: Honor x-amz-retry-after header (1500ms → delay 1.5s)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRetryAfterHeaderHonored() = runTest {
        var attemptCount = 0
        val engine = TestEngine { _, request ->
            attemptCount++
            val headers = HeadersBuilder().apply {
                if (attemptCount == 1) append("x-amz-retry-after", "1500")
            }.build()
            val status = if (attemptCount == 1) HttpStatusCode.InternalServerError else HttpStatusCode.OK
            val resp = HttpResponse(status, headers, HttpBody.Empty)
            val now = Instant.now()
            HttpCall(request, resp, now, now)
        }
        val testClient = SdkHttpClient(engine)

        val retryPolicy = object : RetryPolicy<Unit> {
            override fun evaluate(result: Result<Unit>): RetryDirective =
                if (attemptCount < 2) RetryDirective.RetryError(RetryErrorType.ServerSide)
                else RetryDirective.TerminateAndSucceed
        }

        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)
        val strategy = StandardRetryStrategy {
            delayProvider { jitter = 0.0 }
        }
        op.execution.retryStrategy = strategy
        op.execution.retryPolicy = retryPolicy

        val startTime = currentTime
        op.roundTrip(testClient, Unit)
        val delayMs = currentTime - startTime

        // x-amz-retry-after: 1500ms is within [50ms, 50ms+5000ms], so delay = 1500ms
        assertEquals(1500L, delayMs)
    }

    /**
     * SEP 2.1: x-amz-retry-after: 0 → clamped to t_i (50ms)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRetryAfterClampedToMinimum() = runTest {
        var attemptCount = 0
        val engine = TestEngine { _, request ->
            attemptCount++
            val headers = HeadersBuilder().apply {
                if (attemptCount == 1) append("x-amz-retry-after", "0")
            }.build()
            val status = if (attemptCount == 1) HttpStatusCode.InternalServerError else HttpStatusCode.OK
            val resp = HttpResponse(status, headers, HttpBody.Empty)
            val now = Instant.now()
            HttpCall(request, resp, now, now)
        }
        val testClient = SdkHttpClient(engine)

        val retryPolicy = object : RetryPolicy<Unit> {
            override fun evaluate(result: Result<Unit>): RetryDirective =
                if (attemptCount < 2) RetryDirective.RetryError(RetryErrorType.ServerSide)
                else RetryDirective.TerminateAndSucceed
        }

        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)
        val strategy = StandardRetryStrategy {
            delayProvider { jitter = 0.0 }
        }
        op.execution.retryStrategy = strategy
        op.execution.retryPolicy = retryPolicy

        val startTime = currentTime
        op.roundTrip(testClient, Unit)
        val delayMs = currentTime - startTime

        // x-amz-retry-after: 0 clamped to t_i = 50ms
        assertEquals(50L, delayMs)
    }

    /**
     * SEP 2.1: x-amz-retry-after: 10000 → clamped to t_i + 5s (5050ms)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRetryAfterClampedToMaximum() = runTest {
        var attemptCount = 0
        val engine = TestEngine { _, request ->
            attemptCount++
            val headers = HeadersBuilder().apply {
                if (attemptCount == 1) append("x-amz-retry-after", "10000")
            }.build()
            val status = if (attemptCount == 1) HttpStatusCode.InternalServerError else HttpStatusCode.OK
            val resp = HttpResponse(status, headers, HttpBody.Empty)
            val now = Instant.now()
            HttpCall(request, resp, now, now)
        }
        val testClient = SdkHttpClient(engine)

        val retryPolicy = object : RetryPolicy<Unit> {
            override fun evaluate(result: Result<Unit>): RetryDirective =
                if (attemptCount < 2) RetryDirective.RetryError(RetryErrorType.ServerSide)
                else RetryDirective.TerminateAndSucceed
        }

        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)
        val strategy = StandardRetryStrategy {
            delayProvider { jitter = 0.0 }
        }
        op.execution.retryStrategy = strategy
        op.execution.retryPolicy = retryPolicy

        val startTime = currentTime
        op.roundTrip(testClient, Unit)
        val delayMs = currentTime - startTime

        // x-amz-retry-after: 10000 clamped to t_i + 5000 = 50 + 5000 = 5050ms
        assertEquals(5050L, delayMs)
    }

    /**
     * SEP 2.1: Invalid x-amz-retry-after → fallback to exponential backoff
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRetryAfterInvalidFallsBack() = runTest {
        var attemptCount = 0
        val engine = TestEngine { _, request ->
            attemptCount++
            val headers = HeadersBuilder().apply {
                if (attemptCount == 1) append("x-amz-retry-after", "invalid")
            }.build()
            val status = if (attemptCount == 1) HttpStatusCode.InternalServerError else HttpStatusCode.OK
            val resp = HttpResponse(status, headers, HttpBody.Empty)
            val now = Instant.now()
            HttpCall(request, resp, now, now)
        }
        val testClient = SdkHttpClient(engine)

        val retryPolicy = object : RetryPolicy<Unit> {
            override fun evaluate(result: Result<Unit>): RetryDirective =
                if (attemptCount < 2) RetryDirective.RetryError(RetryErrorType.ServerSide)
                else RetryDirective.TerminateAndSucceed
        }

        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)
        val strategy = StandardRetryStrategy {
            delayProvider { jitter = 0.0 }
        }
        op.execution.retryStrategy = strategy
        op.execution.retryPolicy = retryPolicy

        val startTime = currentTime
        op.roundTrip(testClient, Unit)
        val delayMs = currentTime - startTime

        // Invalid header ignored, falls back to t_i = 50ms
        assertEquals(50L, delayMs)
    }
}
