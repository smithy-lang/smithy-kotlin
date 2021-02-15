/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.feature

import software.aws.clientrt.http.*
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultValidateResponseTest {
    @Test
    fun itThrowsExceptionOnNon200Response() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse {
                return HttpResponse(
                    HttpStatusCode.BadRequest,
                    Headers {},
                    HttpBody.Empty,
                    HttpRequestBuilder().build()
                )
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
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse {
                return HttpResponse(
                    HttpStatusCode.Accepted,
                    Headers {},
                    HttpBody.Empty,
                    HttpRequestBuilder().build()
                )
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
