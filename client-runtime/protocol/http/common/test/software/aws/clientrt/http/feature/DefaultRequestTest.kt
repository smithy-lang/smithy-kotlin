/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.feature

import software.aws.clientrt.http.ExecutionContext
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestContext
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.sdkHttpClient
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultRequestTest {
    @Test
    fun `it sets defaults`() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }
        val client = sdkHttpClient(mockEngine) {
            install(DefaultRequest) {
                method = HttpMethod.POST
                url.host = "localhost"
                url.port = 3000
                headers.append("User-Agent", "MTTUserAgent")
            }
        }

        val ctx = HttpRequestContext(ExecutionContext.build { })
        val builder = HttpRequestBuilder()
        client.requestPipeline.execute(ctx, builder)
        assertEquals(HttpMethod.POST, builder.method)
        assertEquals("localhost", builder.url.host)
        assertEquals(3000, builder.url.port)
        assertEquals("MTTUserAgent", builder.headers["User-Agent"])
    }
}
