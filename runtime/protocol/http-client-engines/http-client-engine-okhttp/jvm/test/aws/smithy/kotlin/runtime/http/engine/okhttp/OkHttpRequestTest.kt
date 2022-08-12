/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OkHttpRequestTest {
    @Test
    fun itConvertsUrls() {
        val url = UrlBuilder().apply {
            scheme = Protocol.HTTPS
            host = "aws.amazon.com"
            path = "/foo%2Fbar/qux"
            parameters {
                append("q", "dogs")
                append("q", "&")
                append("q", "lep ball")
            }
        }.build()

        // check our encoding
        val expectedUrl = "https://aws.amazon.com/foo%2Fbar/qux?q=dogs&q=%26&q=lep%20ball"
        assertEquals(expectedUrl, url.toString())

        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, HttpBody.Empty)

        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext)
        assertEquals("https", actual.url.scheme)
        assertEquals("aws.amazon.com", actual.url.host)
        assertEquals(443, actual.url.port)
        // verify our encoding is kept
        assertEquals("/foo%2Fbar/qux", actual.url.encodedPath)
        assertEquals("q=dogs&q=%26&q=lep%20ball", actual.url.encodedQuery)
    }

    @Test
    fun itConvertsHeaders() {
        val url = Url.parse("https://aws.amazon.com")
        val headers = Headers {
            append("foo", "bar")
            append("Foo", "baz")
            append("bar", "qux")
        }
        val request = HttpRequest(HttpMethod.POST, url, headers, HttpBody.Empty)

        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext)

        assertEquals(3, actual.headers.size)
        assertEquals(listOf("bar", "baz"), actual.headers("FoO"))
    }

    @Test
    fun itAddsSdkTag() {
        val url = Url.parse("https://aws.amazon.com")
        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, HttpBody.Empty)
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext)
        assertEquals(execContext, actual.tag<SdkRequestTag>()?.execContext)
    }

    @Test
    fun itConvertsEmptyHttpBody() {
        val url = Url.parse("https://aws.amazon.com")
        val request = HttpRequest(HttpMethod.GET, url, Headers.Empty, HttpBody.Empty)
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext)

        assertNull(actual.body)
    }

    @Test
    fun itConvertsBytesHttpBody() {
        val url = Url.parse("https://aws.amazon.com")
        val content = "Hello OkHttp from HttpBody.Bytes"
        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, HttpBody.fromBytes(content.encodeToByteArray()))
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext)

        val actualBody = assertNotNull(actual.body)
        assertEquals(request.body.contentLength, actualBody.contentLength())

        val buffer = Buffer()
        actualBody.writeTo(buffer)
        assertEquals(content, buffer.readUtf8())
    }

    @Test
    fun itConvertsStreamingHttpBody() {
        val url = Url.parse("https://aws.amazon.com")
        val content = "Hello OkHttp from HttpBody.Streaming".repeat(1024)
        val contentBytes = content.encodeToByteArray()
        val chan = SdkByteReadChannel(contentBytes)
        val body = object : HttpBody.Streaming() {
            override val contentLength: Long = contentBytes.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, body)
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext)

        val actualBody = assertNotNull(actual.body)
        assertEquals(request.body.contentLength, actualBody.contentLength())

        val buffer = Buffer()
        actualBody.writeTo(buffer)
        assertEquals(content, buffer.readUtf8())
    }
}
