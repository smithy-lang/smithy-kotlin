/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.net.*
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OkHttpRequestTest {

    private val testMetrics = HttpClientMetrics("test", TelemetryProvider.None)

    @Test
    fun itConvertsUrls() {
        val url = Url {
            scheme = Scheme.HTTPS
            host = Host.Domain("aws.amazon.com")
            path.encoded = "/foo%2Fbar/qux"
            parameters.decodedParameters {
                add("q", "dogs")
                add("q", "&")
                add("q", "lep ball")
            }
        }

        // check our encoding
        val expectedUrl = "https://aws.amazon.com/foo%2Fbar/qux?q=dogs&q=%26&q=lep%20ball"
        assertEquals(expectedUrl, url.toString())

        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, HttpBody.Empty)

        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)
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
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

        assertTrue(actual.headers.size >= 3)
        assertEquals(listOf("bar", "baz"), actual.headers("FoO"))
    }

    @Test
    fun itSupportsNonAsciiHeaderValues() {
        val url = Url.parse("https://aws.amazon.com")
        val headers = Headers {
            append("foo", "\uD83E\uDD7D")
        }
        val request = HttpRequest(HttpMethod.POST, url, headers, HttpBody.Empty)

        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

        assertTrue(actual.headers.size >= 1)
        assertEquals(listOf("\uD83E\uDD7D"), actual.headers("foo"))
    }

    // https://github.com/awslabs/smithy-kotlin/issues/1041
    @Test
    fun itAddsAcceptEncodingHeader() {
        val url = Url.parse("https://aws.amazon.com")
        val headers = Headers {
            append("foo", "bar")
        }
        val request = HttpRequest(HttpMethod.POST, url, headers, HttpBody.Empty)

        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

        assertEquals(2, actual.headers.size)
        assertEquals(listOf("bar"), actual.headers("foo"))
        assertEquals(listOf("identity"), actual.headers("Accept-Encoding"))
    }

    // https://github.com/awslabs/smithy-kotlin/issues/1041
    @Test
    fun itDoesNotModifyAcceptEncodingHeaderIfAlreadySet() {
        val url = Url.parse("https://aws.amazon.com")
        val headers = Headers {
            append("foo", "bar")
            append("Accept-Encoding", "gzip")
        }
        val request = HttpRequest(HttpMethod.POST, url, headers, HttpBody.Empty)

        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

        assertEquals(2, actual.headers.size)
        assertEquals(listOf("bar"), actual.headers("foo"))
        assertEquals(listOf("gzip"), actual.headers("Accept-Encoding"))
    }

    @Test
    fun itAddsSdkTag() {
        val url = Url.parse("https://aws.amazon.com")
        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, HttpBody.Empty)
        val execContext = ExecutionContext()

        val callContext = EmptyCoroutineContext

        val actual = request.toOkHttpRequest(execContext, callContext, testMetrics)
        assertEquals(execContext, actual.tag<SdkRequestTag>()?.execContext)
        assertEquals(callContext, actual.tag<SdkRequestTag>()?.callContext)
    }

    @Test
    fun itConvertsEmptyHttpBody() {
        val url = Url.parse("https://aws.amazon.com")
        val request = HttpRequest(HttpMethod.GET, url, Headers.Empty, HttpBody.Empty)
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

        assertNull(actual.body)
    }

    @Test
    fun itConvertsBytesHttpBody() {
        val url = Url.parse("https://aws.amazon.com")
        val content = "Hello OkHttp from HttpBody.Bytes"
        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, HttpBody.fromBytes(content.encodeToByteArray()))
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

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
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long = contentBytes.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }

        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, body)
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

        val actualBody = assertNotNull(actual.body)
        assertEquals(request.body.contentLength, actualBody.contentLength())

        val buffer = Buffer()
        actualBody.writeTo(buffer)
        assertEquals(content, buffer.readUtf8())
    }

    @Test
    fun itUsesContentLengthHeaderWhenContentLengthIsUnknown() {
        val url = Url.parse("https://aws.amazon.com")
        val content = "Hello OkHttp from HttpBody.Streaming".repeat(1024)
        val contentBytes = content.encodeToByteArray()
        val chan = SdkByteReadChannel(contentBytes)
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long = -1
            override fun readFrom(): SdkByteReadChannel = chan
        }
        val expectedContentLength = "5"
        val headers = HeadersBuilder().apply {
            append("Content-Length", expectedContentLength)
        }.build()

        val request = HttpRequest(HttpMethod.POST, url, headers, body)
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

        val actualBody = assertNotNull(actual.body)
        assertEquals(expectedContentLength.toLong(), actualBody.contentLength())

        val buffer = Buffer()
        actualBody.writeTo(buffer)
        assertEquals(content, buffer.readUtf8())
    }

    @Test
    fun itDoesNotUseContentLengthHeaderWhenContentLengthIsDefined() {
        val url = Url.parse("https://aws.amazon.com")
        val content = "Hello OkHttp from HttpBody.Streaming".repeat(1024)
        val contentBytes = content.encodeToByteArray()
        val chan = SdkByteReadChannel(contentBytes)
        val body = object : HttpBody.ChannelContent() {
            override val contentLength: Long = contentBytes.size.toLong()
            override fun readFrom(): SdkByteReadChannel = chan
        }
        val headers = HeadersBuilder().apply {
            append("Content-Length", "9")
        }.build()

        val request = HttpRequest(HttpMethod.POST, url, headers, body)
        val execContext = ExecutionContext()
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

        val actualBody = assertNotNull(actual.body)
        assertEquals(request.body.contentLength, actualBody.contentLength())

        val buffer = Buffer()
        actualBody.writeTo(buffer)
        assertEquals(content, buffer.readUtf8())
    }
}
