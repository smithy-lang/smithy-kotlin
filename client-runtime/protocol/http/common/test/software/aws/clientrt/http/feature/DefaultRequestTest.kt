/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.feature

import software.aws.clientrt.client.ExecutionContext
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
                url.host = "localhost"
                url.port = 3000
                headers.append("User-Agent", "MTTUserAgent")
            }
        }

        val ctx = HttpRequestContext(ExecutionContext())
        val builder = HttpRequestBuilder()
        client.requestPipeline.execute(ctx, builder)
        assertEquals("localhost", builder.url.host)
        assertEquals(3000, builder.url.port)
        assertEquals("MTTUserAgent", builder.headers["User-Agent"])
    }

    @Test
    fun `it does not override`() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }
        val client = sdkHttpClient(mockEngine) {
            install(DefaultRequest) {
                method = HttpMethod.POST
                url.host = "localhost"
                url.port = 3000
                url.path = "/bar"
                headers.append("User-Agent", "MTTUserAgent")
                headers.append("Baz", "qux")
            }
        }

        val ctx = HttpRequestContext(ExecutionContext())
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.PUT
            url.host = "host"
            url.port = 2000
            url.parameters.append("foo", "bar")
            url.path = "/foo"
            headers.append("User-Agent", "quux")
        }

        client.requestPipeline.execute(ctx, builder)
        assertEquals(HttpMethod.PUT, builder.method)
        assertEquals("host", builder.url.host)
        assertEquals(2000, builder.url.port)
        assertEquals("/foo", builder.url.path)
        assertEquals("quux", builder.headers["User-Agent"])
        assertEquals("qux", builder.headers["Baz"])
    }
}
