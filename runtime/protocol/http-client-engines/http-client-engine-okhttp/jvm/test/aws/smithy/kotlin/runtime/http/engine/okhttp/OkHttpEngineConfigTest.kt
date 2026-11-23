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
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okio.blackholeSink
import okio.buffer
import okio.source
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OkHttpEngineConfigTest {
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
    fun testConnectionThatStopsAcknowledgingPingsIsReplaced() = runBlocking {
        SilentHttp2Server().use { server ->
            OkHttpEngine {
                tlsContext { alpn = listOf(AlpnId.H2_PRIOR_KNOWLEDGE) }
                pingInterval = 200.milliseconds
            }.use { engine ->
                val client = SdkHttpClient(engine)
                val url = Url.parse("http://127.0.0.1:${server.port}")
                val request = HttpRequest(HttpMethod.GET, url, Headers.Empty, HttpBody.Empty)

                repeat(2) { client.callAndGiveUpAfter(1.seconds, request) }

                assertTrue(server.connectionsAccepted > 1, "Every call reused the connection that stopped responding")
            }
        }
    }

    private suspend fun SdkHttpClient.callAndGiveUpAfter(deadline: Duration, request: HttpRequest) {
        withTimeoutOrNull(deadline) {
            try {
                call(request)
            } catch (_: HttpException) {
                // the engine closed the connection because a PING wasn't acknowledged
            }
        }
    }

    private class DummyOkHttpClientException : IOException("Custom OkHttpClient interceptor was called")
}

/**
 * An HTTP/2 server (prior knowledge, no TLS) that sends its SETTINGS and then reads everything sent to it without
 * replying, not even to a PING, like a peer that has gone away without closing the connection.
 */
private class SilentHttp2Server : Closeable {
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val connections = CopyOnWriteArrayList<Socket>()

    val port: Int = serverSocket.localPort
    val connectionsAccepted: Int get() = connections.size

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
            socket.source().buffer().readAll(blackholeSink())
        } catch (_: IOException) {
        }
    }

    override fun close() {
        serverSocket.close()
        connections.forEach(Socket::close)
    }
}

private val EMPTY_SETTINGS_FRAME = byteArrayOf(0, 0, 0, 0x4, 0, 0, 0, 0, 0)
