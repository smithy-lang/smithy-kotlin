/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.net.*
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.tracing.*
import aws.smithy.kotlin.runtime.util.MutableAttributes
import aws.smithy.kotlin.runtime.util.mutableAttributes
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class TestTraceSpan(override val parent: TraceSpan?, override val id: String) : TraceSpan {
    override val attributes: MutableAttributes = mutableAttributes()
    override val metadata: TraceSpanMetadata = TraceSpanMetadata(id, id)
    override fun child(name: String): TraceSpan = TestTraceSpan(this, name)
    override fun close() = Unit
    override fun postEvent(event: TraceEvent) = Unit
}

class OkHttpRequestTest {
    @Test
    fun itConvertsUrls() {
        val url = UrlBuilder().apply {
            scheme = Scheme.HTTPS
            host = Host.Domain("aws.amazon.com")
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

        val expectedSpan = TestTraceSpan(null, "a span")
        val callContext = TraceSpanContextElement(expectedSpan)

        val actual = request.toOkHttpRequest(execContext, callContext)
        assertEquals(execContext, actual.tag<SdkRequestTag>()?.execContext)
        assertEquals(expectedSpan, actual.tag<SdkRequestTag>()?.traceSpan)
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
        val body = object : HttpBody.ChannelContent() {
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
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext)

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
        val actual = request.toOkHttpRequest(execContext, EmptyCoroutineContext)

        val actualBody = assertNotNull(actual.body)
        assertEquals(request.body.contentLength, actualBody.contentLength())

        val buffer = Buffer()
        actualBody.writeTo(buffer)
        assertEquals(content, buffer.readUtf8())
    }
}
