/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.middleware

import software.aws.clientrt.http.*
import software.aws.clientrt.http.engine.HttpClientEngineBase
import software.aws.clientrt.http.operation.newTestOperation
import software.aws.clientrt.http.operation.roundTrip
import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpCall
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.testing.runSuspendTest
import software.aws.clientrt.time.Instant
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
