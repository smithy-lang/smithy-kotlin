/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.tracing.NoOpTraceSpan
import aws.smithy.kotlin.runtime.tracing.withRootTraceSpan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultValidateResponseTest {
    @Test
    fun itThrowsExceptionOnNon200Response() = runTest {
        val mockEngine = object : HttpClientEngineBase("test") {
            override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
                val resp = HttpResponse(
                    HttpStatusCode.BadRequest,
                    Headers.Empty,
                    HttpBody.Empty,
                )
                return HttpCall(request, resp, Instant.now(), Instant.now())
            }
        }

        val client = sdkHttpClient(mockEngine)

        val op = newTestOperation<String, String>(HttpRequestBuilder(), "bar")
        op.install(DefaultValidateResponse())

        assertFailsWith(HttpResponseException::class) {
            coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
                op.roundTrip(client, "foo")
            }
        }

        return@runTest
    }

    @Test
    fun itPassesSuccessResponses() = runTest {
        val mockEngine = object : HttpClientEngineBase("test") {
            override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
                val resp = HttpResponse(
                    HttpStatusCode.Accepted,
                    Headers.Empty,
                    HttpBody.Empty,
                )
                return HttpCall(request, resp, Instant.now(), Instant.now())
            }
        }

        val client = sdkHttpClient(mockEngine)

        val op = newTestOperation<String, String>(HttpRequestBuilder(), "bar")
        op.install(DefaultValidateResponse())
        val actual = coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, "foo")
        }
        assertEquals("bar", actual)

        return@runTest
    }
}
