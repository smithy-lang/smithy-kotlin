/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpException
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.engine.AlpnId
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import okio.buffer
import okio.source
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OkHttpEngineConfigTest {
    private val testMetrics = HttpClientMetrics("test", TelemetryProvider.None)

    @Test
    fun testUserClient() = runTest {
        val userClient = OkHttpClient.Builder().apply {
            addInterceptor { throw DummyOkHttpClientException() }
        }.build()

        val engine = OkHttpEngine(userClient)
        val sdkClient = SdkHttpClient(engine)

        val data = "a".repeat(100)
        val url = Url.parse("https://aws.amazon.com")
        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, ByteStream.fromString(data).toHttpBody())

        val ex = assertFailsWith<HttpException> {
            sdkClient.call(request)
        }
        assertTrue(ex.cause is DummyOkHttpClientException)
    }

    @Test
    fun testPingsAreDisabledByDefault() {
        assertEquals(0, OkHttpEngineConfig.Default.buildClient(testMetrics).pingIntervalMillis)
    }

    @Test
    fun testAllowedPingIntervalsReachOkHttp() {
        listOf(1.milliseconds, 1500.milliseconds, Int.MAX_VALUE.milliseconds).forEach { interval ->
            val client = OkHttpEngineConfig { pingInterval = interval }.buildClient(testMetrics)
            assertEquals(interval.inWholeMilliseconds, client.pingIntervalMillis.toLong(), "$interval")
        }
    }

    @Test
    fun testOutOfRangePingIntervalsFailWhenConfigIsBuilt() {
        val intervals = listOf(Duration.ZERO, 500.microseconds, (-1).seconds, (Int.MAX_VALUE + 1L).milliseconds, Duration.INFINITE)
        intervals.forEach { interval ->
            assertFailsWith<IllegalArgumentException>("$interval") { OkHttpEngineConfig { pingInterval = interval } }
        }
    }

    @Test
    fun testToBuilderApplicatorCopiesPingInterval() {
        val original = OkHttpEngineConfig { pingInterval = 30.seconds }
        assertEquals(30.seconds, OkHttpEngineConfig(original.toBuilderApplicator()).pingInterval)
    }

    @Test
    fun testConnectionLeftByCancelledCallsIsReplacedOncePingsGoUnacknowledged() = runBlocking {
        val pingInterval = 1.seconds
        SilentHttp2Server().use { server ->
            h2PriorKnowledgeEngine(pingInterval).use { engine ->
                val client = SdkHttpClient(engine)

                client.callAndCancelOnceServerHasIt(server)
                delay(pingInterval * 3)
                client.callAndCancelOnceServerHasIt(server)

                assertEquals(2, server.connectionsAccepted, "The later call reused the dead connection")
            }
        }
    }

    @Test
    fun testCallInFlightFailsWithProtocolErrorWhenPingGoesUnacknowledged() = runBlocking {
        SilentHttp2Server().use { server ->
            h2PriorKnowledgeEngine(200.milliseconds).use { engine ->
                val ex = assertFailsWith<HttpException> {
                    withTimeout(5.seconds) { SdkHttpClient(engine).call(server.rootRequest()) }
                }

                assertEquals(ErrorCode.PROTOCOL_ERROR, assertIs<StreamResetException>(ex.cause).errorCode)
            }
        }
    }

    private fun h2PriorKnowledgeEngine(pingInterval: Duration) = OkHttpEngine {
        tlsContext { alpn = listOf(AlpnId.H2_PRIOR_KNOWLEDGE) }
        this.pingInterval = pingInterval
    }

    private fun SilentHttp2Server.rootRequest() = HttpRequest(HttpMethod.GET, Url.parse("http://127.0.0.1:$port"), Headers.Empty, HttpBody.Empty)

    private suspend fun SdkHttpClient.callAndCancelOnceServerHasIt(server: SilentHttp2Server) = coroutineScope {
        val requestsBefore = server.requestsReceived
        val call = launch { call(server.rootRequest()) }
        withTimeout(5.seconds) {
            while (server.requestsReceived == requestsBefore) delay(10.milliseconds)
        }
        call.cancelAndJoin()
    }

    private class DummyOkHttpClientException : IOException("Custom OkHttpClient interceptor was called")
}

/**
 * An HTTP/2 server (prior knowledge, no TLS) that sends its SETTINGS and then reads everything sent to it, counting the
 * requests, without replying, not even to a PING, like a peer that has gone away without closing the connection.
 */
private class SilentHttp2Server : Closeable {
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val connections = CopyOnWriteArrayList<Socket>()
    private val requests = AtomicInteger()

    val port: Int = serverSocket.localPort
    val connectionsAccepted: Int get() = connections.size
    val requestsReceived: Int get() = requests.get()

    init {
        thread(isDaemon = true) {
            while (true) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: IOException) {
                    break
                }
                connections += socket
                thread(isDaemon = true) { readUntilClosed(socket) }
            }
        }
    }

    private fun readUntilClosed(socket: Socket) {
        try {
            socket.getOutputStream().apply {
                write(EMPTY_SETTINGS_FRAME)
                flush()
            }
            val source = socket.source().buffer()
            source.skip(CLIENT_CONNECTION_PREFACE_LENGTH)
            while (true) {
                val payloadLength = ((source.readByte().toInt() and 0xff) shl 16) or (source.readShort().toInt() and 0xffff)
                val type = source.readByte()
                source.skip(FRAME_FLAGS_AND_STREAM_ID_LENGTH + payloadLength)
                if (type == HEADERS_FRAME_TYPE) requests.incrementAndGet()
            }
        } catch (_: IOException) {
        }
    }

    override fun close() {
        serverSocket.close()
        connections.forEach(Socket::close)
    }
}

private val EMPTY_SETTINGS_FRAME = byteArrayOf(0, 0, 0, 0x4, 0, 0, 0, 0, 0)
private const val CLIENT_CONNECTION_PREFACE_LENGTH = 24L
private const val FRAME_FLAGS_AND_STREAM_ID_LENGTH = 5L
private const val HEADERS_FRAME_TYPE: Byte = 0x1
