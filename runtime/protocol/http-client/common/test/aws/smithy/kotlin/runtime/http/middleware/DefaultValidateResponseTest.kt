/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultValidateResponseTest {
    @Test
    fun itThrowsExceptionOnNon200Response() = runTest {
        val mockEngine = TestEngine { _, request ->
            val resp = HttpResponse(
                HttpStatusCode.BadRequest,
                Headers.Empty,
                HttpBody.Empty,
            )
            HttpCall(request, resp, Instant.now(), Instant.now())
        }

        val client = SdkHttpClient(mockEngine)

        val op = newTestOperation<String, String>(HttpRequestBuilder(), "bar")
        op.install(DefaultValidateResponse())

        assertFailsWith(HttpResponseException::class) {
            op.roundTrip(client, "foo")
        }

        return@runTest
    }

    @Test
    fun itPassesSuccessResponses() = runTest {
        val mockEngine = TestEngine { _, request ->
            val resp = HttpResponse(
                HttpStatusCode.Accepted,
                Headers.Empty,
                HttpBody.Empty,
            )
            HttpCall(request, resp, Instant.now(), Instant.now())
        }

        val client = SdkHttpClient(mockEngine)

        val op = newTestOperation<String, String>(HttpRequestBuilder(), "bar")
        op.install(DefaultValidateResponse())
        val actual = op.roundTrip(client, "foo")
        assertEquals("bar", actual)

        return@runTest
    }
}
