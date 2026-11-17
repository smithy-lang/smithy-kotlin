/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.httptest.TestWithLocalServer
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.use
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val READ_TIMEOUT = 1.seconds

// long enough that a test only passes if the engine enforces READ_TIMEOUT
private val SERVER_STALL = 30.seconds

// bounds each test so a missing read timeout fails it instead of hanging the build
private val TEST_TIMEOUT = 10.seconds

private const val CHUNK = "0123456789abcdef"
private const val CHUNK_COUNT = 4

// shorter than READ_TIMEOUT per gap, longer than READ_TIMEOUT in total
private val CHUNK_GAP = 400.milliseconds

class ReadTimeoutTest : TestWithLocalServer() {
    override val server = embeddedServer(CIO, serverPort) {
        routing {
            get("/no-response") {
                delay(SERVER_STALL)
                call.respondText("too late")
            }

            get("/stalled-body") {
                call.respondBytesWriter(contentLength = CHUNK.length * 2L) {
                    writeFully(CHUNK.encodeToByteArray())
                    flush()
                    delay(SERVER_STALL)
                    writeFully(CHUNK.encodeToByteArray())
                }
            }

            get("/slow-body") {
                call.respondBytesWriter(contentLength = CHUNK.length.toLong() * CHUNK_COUNT) {
                    repeat(CHUNK_COUNT) {
                        delay(CHUNK_GAP)
                        writeFully(CHUNK.encodeToByteArray())
                        flush()
                    }
                }
            }

            post("/echo") {
                val body = call.receiveText()
                call.respondText(body)
            }
        }
    }

    private fun request(path: String, method: HttpMethod = HttpMethod.GET) = HttpRequestBuilder().apply {
        this.method = method
        url {
            scheme = Scheme.HTTP
            host = Host.Domain(testHost)
            port = serverPort
            this.path.decoded = path
        }
    }

    private fun testWithEngine(block: suspend CoroutineScope.(SdkHttpClient) -> Unit) = runBlocking {
        CrtHttpEngine {
            connectTimeout = 1.seconds
            socketReadTimeout = READ_TIMEOUT
        }.use { engine ->
            withTimeout(TEST_TIMEOUT) {
                block(SdkHttpClient(engine))
            }
        }
    }

    private fun assertReadTimeout(ex: HttpException) {
        assertEquals(HttpErrorCode.SOCKET_TIMEOUT, ex.errorCode)
        assertTrue(ex.sdkErrorMetadata.isRetryable)
    }

    private suspend fun HttpCall.bodyText(): String = response.body.readAll()!!.decodeToString()

    @Test
    fun testNoResponseHeadersTimesOut() = testWithEngine { client ->
        val ex = assertFailsWith<HttpException> {
            client.call(request("/no-response")).complete()
        }
        assertReadTimeout(ex)
    }

    @Test
    fun testStalledResponseBodyTimesOut() = testWithEngine { client ->
        val call = client.call(request("/stalled-body"))
        try {
            assertEquals(HttpStatusCode.OK, call.response.status)
            val ex = assertFailsWith<HttpException> {
                call.response.body.readAll()
            }
            assertReadTimeout(ex)
        } finally {
            call.complete()
        }
    }

    @Test
    fun testSlowButSteadyResponseBodySucceeds() = testWithEngine { client ->
        val call = client.call(request("/slow-body"))
        try {
            assertEquals(CHUNK.repeat(CHUNK_COUNT), call.bodyText())
        } finally {
            call.complete()
        }
    }

    @Test
    fun testSlowRequestBodyDoesNotCountTowardReadTimeout() = testWithEngine { client ->
        // the upload takes longer than the read timeout, so the timer must not start until it's fully sent
        val payload = CHUNK.repeat(CHUNK_COUNT)
        val bodyChan = SdkByteChannel()
        val req = request("/echo", HttpMethod.POST).apply {
            body = object : HttpBody.ChannelContent() {
                override val contentLength: Long = payload.length.toLong()
                override fun readFrom(): SdkByteReadChannel = bodyChan
            }
        }

        val writer = launch {
            repeat(CHUNK_COUNT) {
                delay(CHUNK_GAP)
                bodyChan.write(SdkBuffer().apply { write(CHUNK.encodeToByteArray()) })
                bodyChan.flush()
            }
            bodyChan.close()
        }

        val call = client.call(req)
        try {
            assertEquals(HttpStatusCode.OK, call.response.status)
            assertEquals(payload, call.bodyText())
        } finally {
            call.complete()
            writer.join()
        }
    }
}
