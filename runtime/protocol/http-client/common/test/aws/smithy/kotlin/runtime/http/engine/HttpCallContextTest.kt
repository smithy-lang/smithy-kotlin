/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.coroutines.coroutineContext as currentCoroutineContext

class HttpCallContextTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testStructuredConcurrency() = runTest {
        val engine = object : HttpClientEngineBase("test-engine") {
            override val config: HttpClientEngineConfig = HttpClientEngineConfig.Default
            override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
                val callContext = callContext()
                val currentContext = currentCoroutineContext

                val callChildren = callContext.job.children.toList()

                // the callContext is the parent of the coroutine executing roundTrip()
                assertEquals(1, callChildren.size)
                assertEquals(callContext.job, currentContext.job.parent)
                assertEquals(callChildren.first().job, currentContext.job)

                // call context should be a child of the engine
                assertEquals(callContext.job, coroutineContext.job.children.toList().first())

                val response = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
                return HttpCall(request, response, Instant.now(), Instant.now(), callContext)
            }
        }

        val client = SdkHttpClient(engine)
        val call = client.call(HttpRequestBuilder())
        call.complete()

        // the child coroutine launched for `roundTrip` should be completed
        assertTrue(call.coroutineContext.job.children.toList().isEmpty())
        assertTrue(call.coroutineContext.job.isCompleted)
    }
}
