/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategyOptions
import aws.smithy.kotlin.runtime.retries.delay.DelayProvider
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucket
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucketOptions
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RetryMiddlewareTest {
    private val mockEngine = object : HttpClientEngineBase("test") {
        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
            val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
            return HttpCall(request, resp, Instant.now(), Instant.now())
        }
    }
    private val client = SdkHttpClient(mockEngine)

    private val policy = object : RetryPolicy<Any?> {
        var attempts = 0
        override fun evaluate(result: Result<Any?>): RetryDirective =
            if (attempts < 1) {
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

        val delayProvider = DelayProvider { }
        val strategy = StandardRetryStrategy(
            StandardRetryStrategyOptions.Default,
            StandardRetryTokenBucket(StandardRetryTokenBucketOptions.Default),
            delayProvider,
        )

        op.execution.retryStrategy = strategy
        op.execution.retryPolicy = policy

        op.roundTrip(client, Unit)
        val attempts = op.context.attributes[HttpOperationContext.HttpCallList].size
        assertEquals(2, attempts)
    }
}
