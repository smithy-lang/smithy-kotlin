/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.*
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpResponseTest {
    @Test
    fun testToSdkResponseEmptyBody() = runTest {
        val request = HttpRequest(HttpMethod.GET, Url.parse("https://aws.amazon.com"), Headers.Empty, HttpBody.Empty)
        val execContext = ExecutionContext()
        val okRequest = request.toOkHttpRequest(execContext, EmptyCoroutineContext)

        val okResponse = Response.Builder().apply {
            protocol(Protocol.HTTP_1_1)
            code(200)
            message("OK")
            addHeader("foo", "bar")
            request(okRequest)
        }.build()

        val sdkResponse = okResponse.toSdkResponse()
        assertIs<HttpBody.Empty>(sdkResponse.body)
    }

    @Test
    fun testToSdkResponseWithBody() = runTest {
        val request = HttpRequest(HttpMethod.GET, Url.parse("https://aws.amazon.com"), Headers.Empty, HttpBody.Empty)

        val execContext = ExecutionContext()
        val okRequest = request.toOkHttpRequest(execContext, EmptyCoroutineContext)

        val content = "Hello from OkHttp".encodeToByteArray()

        val okResponse = Response.Builder().apply {
            protocol(Protocol.HTTP_1_1)
            code(200)
            body(content.toResponseBody())
            message("OK")
            addHeader("foo", "bar")
            request(okRequest)
        }.build()

        val sdkResponse = okResponse.toSdkResponse()
        assertEquals(HttpStatusCode.OK, sdkResponse.status)
        assertEquals("bar", sdkResponse.headers["foo"])
        assertEquals(content.size.toLong(), sdkResponse.body.contentLength)

        val actualBody = async {
            sdkResponse.body.readAll() ?: error("no body")
        }.await()

        assertContentEquals(content, actualBody)
    }
}
