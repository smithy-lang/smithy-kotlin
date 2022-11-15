/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
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

        val sdkResponse = okResponse.toSdkResponse(EmptyCoroutineContext)
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

        val sdkResponse = okResponse.toSdkResponse(EmptyCoroutineContext)
        assertEquals(HttpStatusCode.OK, sdkResponse.status)
        assertEquals("bar", sdkResponse.headers["foo"])
        assertEquals(content.size.toLong(), sdkResponse.body.contentLength)

        val actualBody = async {
            sdkResponse.body.readAll() ?: error("no body")
        }.await()

        assertContentEquals(content, actualBody)
    }

    @Test
    fun testCallContextCancelsWriter() = runTest {
        // call context cancelled should result in no children left
        val request = HttpRequest(HttpMethod.GET, Url.parse("https://aws.amazon.com"), Headers.Empty, HttpBody.Empty)

        val execContext = ExecutionContext()
        val callJob = Job(coroutineContext.job)
        val callContext = coroutineContext + callJob
        val okRequest = request.toOkHttpRequest(execContext, callContext)

        // something sufficiently large enough that a single write doesn't cause the coroutine to immediately complete
        val content = ByteArray(16 * 1024 * 1024)

        val okResponse = Response.Builder().apply {
            protocol(Protocol.HTTP_1_1)
            code(200)
            body(content.toResponseBody())
            message("OK")
            addHeader("foo", "bar")
            request(okRequest)
        }.build()

        val sdkResponse = okResponse.toSdkResponse(callContext)
        yield()

        val body = sdkResponse.body
        assertIs<HttpBody.Streaming>(body)
        val ch = body.readFrom()
        assertFalse(ch.isClosedForWrite)

        assertEquals(1, callContext.job.children.toList().size)
        callJob.cancel()
        callJob.complete()
        callJob.join()
        assertEquals(0, callContext.job.children.toList().size)
        assertTrue(ch.isClosedForWrite)
    }

    @Test
    fun testSourceReadError(): Unit = runBlocking {
        val request = HttpRequest(HttpMethod.GET, Url.parse("https://aws.amazon.com"), Headers.Empty, HttpBody.Empty)

        val execContext = ExecutionContext()

        // replace default exception handler which will print out the stack trace by default.
        // We are expecting an exception so this message is misleading/confusing
        val exHandler = CoroutineExceptionHandler { _, _ -> }

        // don't tie this to the current job or else it will tear down the test as well
        val callJob = Job()
        val callContext = coroutineContext + callJob + exHandler
        val okRequest = request.toOkHttpRequest(execContext, callContext)

        val content = ByteArray(16 * 1024 * 1024)
        val buffer = Buffer()
        buffer.write(content)
        val okSource = object : Source {
            private var cnt = 0
            override fun read(sink: Buffer, byteCount: Long): Long {
                // wait to fail until we've gotten setup and are waiting on the channel to be read to make progress
                // or else we won't be able to make assertions
                if (cnt > 0) {
                    throw RuntimeException("test read error")
                }
                cnt++
                return buffer.read(sink, byteCount)
            }
            override fun close() {}
            override fun timeout(): Timeout = Timeout.NONE
        }

        val okBody = object : ResponseBody() {
            override fun contentLength(): Long = 1024
            override fun contentType(): MediaType? = null
            override fun source(): BufferedSource = okSource.buffer()
        }

        val okResponse = Response.Builder().apply {
            protocol(Protocol.HTTP_1_1)
            code(200)
            body(okBody)
            message("OK")
            addHeader("foo", "bar")
            request(okRequest)
        }.build()

        val sdkResponse = okResponse.toSdkResponse(callContext)
        yield()

        val body = sdkResponse.body
        assertIs<HttpBody.Streaming>(body)
        val ch = body.readFrom()
        assertFalse(ch.isClosedForWrite)

        assertEquals(1, callContext.job.children.toList().size)

        assertFailsWith<RuntimeException> {
            ch.readRemaining()
        }.message.shouldContain("test read error")
        callJob.complete()
        callJob.join()
        assertEquals(0, callContext.job.children.toList().size)
    }
}
