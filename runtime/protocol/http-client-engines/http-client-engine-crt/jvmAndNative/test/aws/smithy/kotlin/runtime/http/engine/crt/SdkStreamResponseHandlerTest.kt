/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.CRT
import aws.sdk.kotlin.crt.http.*
import aws.sdk.kotlin.crt.io.byteArrayBuffer
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpErrorCode
import aws.smithy.kotlin.runtime.http.HttpException
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSink
import aws.smithy.kotlin.runtime.io.readAll
import aws.smithy.kotlin.runtime.io.readToBuffer
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.*

class SdkStreamResponseHandlerTest {
    private class MockHttpStream(private val statusCode: Int) : HttpStream {
        var closed: Boolean = false
        var statusReadAfterClose: Boolean = false

        // records every incrementWindow(size) call so tests can assert window replenishment
        val windowIncrements = mutableListOf<Int>()
        val totalWindowIncremented: Int get() = windowIncrements.sum()

        override val responseStatusCode: Int
            get() {
                if (closed) statusReadAfterClose = true
                return statusCode
            }

        override fun activate() {}
        override suspend fun writeChunk(chunkData: ByteArray, isFinalChunk: Boolean) {
            TODO("Not yet implemented")
        }

        override fun close() {
            closed = true
        }
        override fun incrementWindow(size: Int) {
            windowIncrements.add(size)
        }
    }

    private class MockHttpClientConnection : HttpClientConnection {
        override val id: String = "<mock connection>"
        override val version: HttpVersion = HttpVersion.HTTP_1_1
        var isClosed: Boolean = false
        override fun close() {
            isClosed = true
        }
        override fun makeRequest(httpReq: HttpRequest, handler: HttpStreamResponseHandler): HttpStream = throw UnsupportedOperationException("not implemented for test")
        override fun shutdown() { }
    }

    private val mockConn = MockHttpClientConnection()

    @Test
    fun testWaitSuccessResponse() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        launch {
            val headers = listOf(
                HttpHeader("foo", "bar"),
                HttpHeader("baz", "qux"),
            )
            handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
            handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
        }

        // should be signalled as soon as headers are available
        val resp = handler.waitForResponse()
        assertEquals(HttpStatusCode.OK, resp.status)

        assertTrue(resp.body is HttpBody.Empty)
        handler.onResponseComplete(stream, 0)

        assertFalse(mockConn.isClosed)
        handler.complete()
        assertTrue(mockConn.isClosed)
    }

    @Test
    fun testWaitNoHeaders() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        launch {
            handler.onResponseComplete(stream, 0)
        }

        val resp = handler.waitForResponse()
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun testResponseSignalledBeforeStreamClosed() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(204)
        launch {
            handler.onResponseComplete(stream, 0)
        }

        val resp = handler.waitForResponse()
        assertEquals(HttpStatusCode.NoContent, resp.status)

        // the status code must have been read while the stream was still valid (before close)
        assertTrue(stream.closed)
        assertFalse(stream.statusReadAfterClose, "responseStatusCode was read after the stream was closed")
    }

    @Test
    fun testWaitAbortedResponse() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        launch {
            handler.onResponseComplete(stream, -1)
        }

        // failed engine execution should raise an exception
        assertFails {
            handler.waitForResponse()
        }
    }

    @Test
    fun testRespBodyCreated() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        launch {
            val headers = listOf(
                HttpHeader("Content-Length", "72"),
            )
            handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
            handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
        }

        // should be signalled as soon as headers are available
        val resp = handler.waitForResponse()
        assertEquals(HttpStatusCode.OK, resp.status)

        assertEquals(72, resp.body.contentLength)
        assertTrue(resp.body is HttpBody.ChannelContent)
        val respChan = (resp.body as HttpBody.ChannelContent).readFrom()
        assertFalse(respChan.isClosedForWrite)

        assertFalse(mockConn.isClosed)
        handler.onResponseComplete(stream, 0)
        yield()
        assertTrue(respChan.isClosedForWrite)
    }

    @Test
    fun testRespBody() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        val data = "Fool of a Took! Throw yourself in next time and rid us of your stupidity!"
        launch {
            val headers = listOf(
                HttpHeader("Content-Length", "${data.length}"),
            )
            handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
            handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
            handler.onResponseBody(stream, byteArrayBuffer(data.encodeToByteArray()))
            handler.onResponseComplete(stream, 0)
        }

        // should be signalled as soon as headers are available
        val resp = handler.waitForResponse()
        assertEquals(HttpStatusCode.OK, resp.status)

        assertEquals(data.length.toLong(), resp.body.contentLength)
        assertTrue(resp.body is HttpBody.ChannelContent)
        val respChan = (resp.body as HttpBody.ChannelContent).readFrom()

        assertTrue(respChan.isClosedForWrite)

        assertEquals(data, respChan.readToBuffer().readUtf8())
    }

    @Test
    fun testStreamError() = runTest {
        CRT.initRuntime() // CRT needs to be initialized for human-readable error codes
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        val data = "foo bar"
        val socketClosedEc = 1051
        launch {
            val headers = listOf(
                HttpHeader("Content-Length", "${data.length}"),
            )
            handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
            handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
            handler.onResponseBody(stream, byteArrayBuffer("foo".encodeToByteArray()))
            handler.onResponseComplete(stream, socketClosedEc)
        }

        // should be signalled as soon as headers are available
        val resp = handler.waitForResponse()
        assertEquals(HttpStatusCode.OK, resp.status)

        assertEquals(data.length.toLong(), resp.body.contentLength)
        val respChan = (resp.body as HttpBody.ChannelContent).readFrom()

        val ex = assertFailsWith<HttpException> {
            respChan.readAll(SdkSink.blackhole())
        }

        ex.message.shouldContain("socket is closed.; crtErrorCode=$socketClosedEc")
        assertEquals(HttpErrorCode.CONNECTION_CLOSED, ex.errorCode)
    }

    @Test
    fun testRespBodyMultipleChunksReassembledInOrder() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        val chunks = listOf("Frodo ", "Sam ", "Merry ", "Pippin")
        val full = chunks.joinToString("")
        launch {
            val headers = listOf(HttpHeader("Content-Length", "${full.length}"))
            handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
            handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
            chunks.forEach { handler.onResponseBody(stream, byteArrayBuffer(it.encodeToByteArray())) }
            handler.onResponseComplete(stream, 0)
        }

        val resp = handler.waitForResponse()
        val respChan = (resp.body as HttpBody.ChannelContent).readFrom()
        assertEquals(full, respChan.readToBuffer().readUtf8())
    }

    @Test
    fun testWindowIsReplenishedAsBodyIsConsumed() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        val data = "Fool of a Took!".encodeToByteArray()
        // deliver headers + a body chunk but leave the stream active (not yet complete) so the window is live
        val headers = listOf(HttpHeader("Content-Length", "${data.size}"))
        handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
        handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
        handler.onResponseBody(stream, byteArrayBuffer(data))

        val resp = handler.waitForResponse()
        val respChan = (resp.body as HttpBody.ChannelContent).readFrom()

        // no window credit should be returned until bytes are actually consumed
        assertEquals(0, stream.totalWindowIncremented)

        // consume the buffered chunk while the stream is still active; window must be returned for the bytes read
        val sink = SdkBuffer()
        val rc = respChan.read(sink, data.size.toLong())
        assertContentEquals(data, sink.readByteArray())
        assertEquals(rc.toInt(), stream.totalWindowIncremented)
        assertEquals(data.size, stream.totalWindowIncremented)
    }

    @Test
    fun testWindowReplenishedIncrementallyOnPartialReads() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        val data = ByteArray(32) { it.toByte() }
        // leave the stream active so window increments are delivered to it as the consumer reads
        val headers = listOf(HttpHeader("Content-Length", "${data.size}"))
        handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
        handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
        handler.onResponseBody(stream, byteArrayBuffer(data))

        val resp = handler.waitForResponse()
        val respChan = (resp.body as HttpBody.ChannelContent).readFrom()

        val sink = SdkBuffer()
        val firstRead = respChan.read(sink, 10)
        assertTrue(firstRead > 0, "expected to read some bytes")
        // window returned so far must equal exactly what was read (no more, no less)
        assertEquals(firstRead.toInt(), stream.totalWindowIncremented)

        // read the remainder; total window returned must equal total bytes read back
        var total = firstRead
        while (total < data.size) {
            val rc = respChan.read(sink, data.size - total)
            if (rc <= 0) break
            total += rc
        }
        assertEquals(data.size.toLong(), total)
        assertEquals(data.size, stream.totalWindowIncremented)
    }

    @Test
    fun testForceCloseMidBodyWakesSuspendedReader() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)
        launch {
            val headers = listOf(HttpHeader("Content-Length", "1024"))
            handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
            handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
            handler.onResponseBody(stream, byteArrayBuffer("partial".encodeToByteArray()))
            // stream never completes: simulate the request job finishing early (e.g. cancellation)
            handler.complete()
        }

        val resp = handler.waitForResponse()
        val respChan = (resp.body as HttpBody.ChannelContent).readFrom()

        // reading past the buffered data must not hang; force-close wakes the reader with a failure
        val ex = assertFails {
            withTimeout(5000) {
                respChan.readAll(SdkSink.blackhole())
            }
        }
        assertTrue(ex is CancellationException, "expected reader to be woken with a CancellationException, got $ex")
    }

    @Test
    fun testConsumerClosingBodyEarlyDoesNotThrowFromCallback() = runTest {
        val handler = SdkStreamResponseHandler(mockConn, coroutineContext, DEFAULT_WINDOW_SIZE_BYTES)
        val stream = MockHttpStream(200)

        val headers = listOf(HttpHeader("Content-Length", "1024"))
        handler.onResponseHeaders(stream, 200, HttpHeaderBlock.MAIN.blockType, headers)
        handler.onResponseHeadersDone(stream, HttpHeaderBlock.MAIN.blockType)
        handler.onResponseBody(stream, byteArrayBuffer("first".encodeToByteArray()))

        val resp = handler.waitForResponse()
        val respChan = (resp.body as HttpBody.ChannelContent).readFrom()

        // consumer abandons the body early
        respChan.cancel(null)

        // subsequent body callbacks must not throw out of the CRT native callback; bytes are discarded
        val discarded = handler.onResponseBody(stream, byteArrayBuffer("more data".encodeToByteArray()))
        assertEquals("more data".length, discarded)
        assertTrue(stream.closed)
    }
}
