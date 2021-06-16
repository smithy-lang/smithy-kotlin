/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultValidateResponseTest {
    @Test
    fun itThrowsExceptionOnNon200Response() = runSuspendTest {
        val mockEngine = object : HttpClientEngineBase("test") {
            override suspend fun roundTrip(request: HttpRequest): HttpCall {
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
        op.install(DefaultValidateResponse)

        assertFailsWith(HttpResponseException::class) {
            op.roundTrip(client, "foo")
        }

        return@runSuspendTest
    }

    @Test
    fun itPassesSuccessResponses() = runSuspendTest {
        val mockEngine = object : HttpClientEngineBase("test") {
            override suspend fun roundTrip(request: HttpRequest): HttpCall {
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
        op.install(DefaultValidateResponse)
        val actual = op.roundTrip(client, "foo")
        assertEquals("bar", actual)

        return@runSuspendTest
    }
}
