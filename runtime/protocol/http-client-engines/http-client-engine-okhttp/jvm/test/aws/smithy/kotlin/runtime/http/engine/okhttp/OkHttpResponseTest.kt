/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.retries.policy.StandardRetryPolicy
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class OkHttpResponseTest {

    private val testMetrics = HttpClientMetrics("test", TelemetryProvider.None)

    @Test
    fun testToSdkResponseEmptyBody() = runTest {
        val request = HttpRequest(HttpMethod.GET, Url.parse("https://aws.amazon.com"), Headers.Empty, HttpBody.Empty)
        val execContext = ExecutionContext()
        val okRequest = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

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
        val okRequest = request.toOkHttpRequest(execContext, EmptyCoroutineContext, testMetrics)

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

    // Response body backed by a source that fails after emitting [readableBytes] bytes, simulating a
    // dropped/truncated connection while the body is being streamed.
    private fun failingResponseBody(
        error: IOException,
        contentLength: Long,
        readableBytes: Long = 0L,
    ): okhttp3.ResponseBody {
        val source = object : Source {
            private var remaining = readableBytes
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (remaining <= 0L) throw error
                val n = minOf(byteCount, remaining)
                repeat(n.toInt()) { sink.writeByte('a'.code) }
                remaining -= n
                return n
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {}
        }
        return source.buffer().asResponseBody("text/plain".toMediaTypeOrNull(), contentLength)
    }

    private fun failingOkResponse(body: okhttp3.ResponseBody): Response {
        val request = HttpRequest(HttpMethod.GET, Url.parse("https://aws.amazon.com"), Headers.Empty, HttpBody.Empty)
        val okRequest = request.toOkHttpRequest(ExecutionContext(), EmptyCoroutineContext, testMetrics)
        return Response.Builder().apply {
            protocol(Protocol.HTTP_1_1)
            code(200)
            body(body)
            message("OK")
            request(okRequest)
        }.build()
    }

    @Test
    fun testBodyReadIoErrorSurfacesAsRetryableHttpException() = runTest {
        // A raw IOException raised while reading the response body must be wrapped as a retryable HttpException
        // so the retry policy retries it rather than treating it as a non-retryable failure.
        val okResponse = failingOkResponse(failingResponseBody(IOException("connection reset"), contentLength = 128L))

        val sdkResponse = okResponse.toSdkResponse()

        val ex = assertFailsWith<HttpException> {
            sdkResponse.body.readAll()
        }
        assertTrue(ex.sdkErrorMetadata.isRetryable)
        assertEquals(HttpErrorCode.SDK_UNKNOWN, ex.errorCode)
    }

    @Test
    fun testBodyReadTimeoutMapsToSocketTimeout() = runTest {
        // Errors mid-stream (after some bytes are read) are also mapped, and the error code is preserved.
        val okResponse = failingOkResponse(
            failingResponseBody(SocketTimeoutException("read timeout"), contentLength = 128L, readableBytes = 8L),
        )

        val sdkResponse = okResponse.toSdkResponse()

        val ex = assertFailsWith<HttpException> {
            sdkResponse.body.readAll()
        }
        assertTrue(ex.sdkErrorMetadata.isRetryable)
        assertEquals(HttpErrorCode.SOCKET_TIMEOUT, ex.errorCode)
    }

    @Test
    fun testBodyReadFaultIsRetriedByPolicy() = runTest {
        // End-to-end: a body-read network fault must be evaluated as a retryable (transient) error by the
        // standard retry policy, not terminated as a non-retryable failure.
        val okResponse = failingOkResponse(failingResponseBody(IOException("connection reset"), contentLength = 128L))
        val sdkResponse = okResponse.toSdkResponse()
        val ex = assertFailsWith<HttpException> {
            sdkResponse.body.readAll()
        }

        val directive = StandardRetryPolicy.Default.evaluate(Result.failure(ex))
        assertEquals(RetryDirective.RetryError(RetryErrorType.Transient), directive)
    }
}
