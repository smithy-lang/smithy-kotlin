/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.crt.ReadChannelBodyStream
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class RequestConversionTest {
    private fun byteStreamFromContents(contents: String): ByteStream =
        object : ByteStream.ChannelStream() {
            override val contentLength: Long = contents.length.toLong()
            override fun readFrom(): SdkByteReadChannel =
                SdkByteReadChannel(contents.encodeToByteArray())
        }

    @Test
    fun testUri() {
        val request = HttpRequest(
            HttpMethod.GET,
            Url.parse("https://test.aws.com?foo=bar"),
            Headers.Empty,
            HttpBody.Empty,
        )
        val uri = request.uri
        assertEquals("https://test.aws.com", uri.toString())
    }

    @Test
    fun testSdkToCrtRequestBytesBody() {
        val body = HttpBody.fromBytes("foobar".encodeToByteArray())
        val request = HttpRequest(
            HttpMethod.POST,
            Url.parse("https://test.aws.com?foo=bar"),
            Headers.Empty,
            body,
        )

        val crtRequest = request.toCrtRequest(EmptyCoroutineContext)
        assertEquals("POST", crtRequest.method)
        assertFalse(crtRequest.body is ReadChannelBodyStream)
    }

    @Test
    fun testSdkToCrtRequestStreamingBody() {
        val stream = byteStreamFromContents("foobar")
        val body = stream.toHttpBody()
        val request = HttpRequest(
            HttpMethod.POST,
            Url.parse("https://test.aws.com?foo=bar"),
            Headers.Empty,
            body,
        )

        val testContext = EmptyCoroutineContext + Job()
        val crtRequest = request.toCrtRequest(testContext)
        assertEquals("POST", crtRequest.method)
        val crtBody = crtRequest.body as ReadChannelBodyStream
        crtBody.cancel()
    }

    @Test
    fun testEngineAddsContentLengthHeader() {
        val stream = byteStreamFromContents("foobar")
        val body = stream.toHttpBody()
        val request = HttpRequest(
            HttpMethod.POST,
            Url.parse("https://test.aws.com?foo=bar"),
            Headers.Empty,
            body,
        )

        val testContext = EmptyCoroutineContext + Job()
        val crtRequest = request.toCrtRequest(testContext)
        assertEquals("6", crtRequest.headers["Content-Length"])

        val crtBody = crtRequest.body as ReadChannelBodyStream
        crtBody.cancel()
    }

    @Test
    fun testEngineAddsContentLengthHeaderForEmptyBody() {
        val request = HttpRequest(
            HttpMethod.POST,
            Url.parse("https://test.aws.com?foo=bar"),
            Headers.Empty,
            HttpBody.Empty,
        )

        val testContext = EmptyCoroutineContext + Job()
        val crtRequest = request.toCrtRequest(testContext)
        assertEquals("0", crtRequest.headers["Content-Length"])
    }

    @Test
    fun testEngineSetsNullBodyForChannelContentChunkedRequests() {
        val testData = ByteArray(1024) { 0 }

        val request = HttpRequest(
            HttpMethod.POST,
            Url.parse("https://test.aws.com?foo=bar"),
            Headers.invoke { append("Transfer-Encoding", "chunked") },
            object : HttpBody.ChannelContent() {
                override val contentLength: Long = testData.size.toLong()
                override fun readFrom(): SdkByteReadChannel = SdkByteReadChannel(testData)
            },
        )

        val testContext = EmptyCoroutineContext + Job()
        val crtRequest = request.toCrtRequest(testContext)
        assertNotNull(request.body)
        assertNull(crtRequest.body)
    }

    @Test
    fun testEngineSetsNullBodyForSourceContentChunkedRequests() {
        val testData = ByteArray(1024) { 0 }

        val request = HttpRequest(
            HttpMethod.POST,
            Url.parse("https://test.aws.com?foo=bar"),
            Headers.invoke { append("Transfer-Encoding", "chunked") },
            object : HttpBody.SourceContent() {
                override val contentLength: Long = testData.size.toLong()
                override fun readFrom(): SdkSource = testData.source()
            },
        )

        val testContext = EmptyCoroutineContext + Job()
        val crtRequest = request.toCrtRequest(testContext)
        assertNotNull(request.body)
        assertNull(crtRequest.body)
    }
}
