/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class Http2IntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: CrtHttpEngine
    private lateinit var client: SdkHttpClient

    @BeforeTest
    fun setup() {
        val serverCert = HeldCertificate.Builder()
            .commonName("localhost")
            .build()

        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCert)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        server.start()

        engine = CrtHttpEngine {
            verifyPeer = false
        }
        client = SdkHttpClient(engine)
    }

    @AfterTest
    fun teardown() {
        engine.close()
        server.close()
    }

    @Test
    fun testHttp2Request() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("Hello HTTP/2")
                .addHeader("content-type", "text/plain")
                .build(),
        )

        val request = HttpRequestBuilder().apply {
            method = HttpMethod.GET
            url {
                scheme = Scheme.HTTPS
                host = Host.Domain("localhost")
                port = server.port
                path.encoded = "/test"
            }
        }.build()

        val call = client.call(request)
        val response = call.response

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello HTTP/2", response.body.readAll()?.decodeToString())

        val recordedRequest = server.takeRequest()
        assertNotNull(recordedRequest)
        assertEquals("HTTP/2", recordedRequest.version)
    }

    @Test
    fun testHttp2BidirectionalStreaming() = runBlocking {
        val responseBody = okio.Buffer()
        launch {
            delay(100.milliseconds)
            responseBody.writeUtf8("resp1\n")
            delay(100.milliseconds)
            responseBody.writeUtf8("resp2\n")
            delay(100.milliseconds)
            responseBody.writeUtf8("resp3\n")
        }

        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(responseBody)
                .addHeader("content-type", "text/plain")
                .build(),
        )

        val requestChannel = SdkByteChannel(true)

        val request = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url {
                scheme = Scheme.HTTPS
                host = Host.Domain("localhost")
                port = server.port
                path.encoded = "/stream"
            }
            body = object : HttpBody.ChannelContent() {
                override val isDuplex = true
                override val contentLength: Long? = null
                override fun readFrom(): SdkByteReadChannel = requestChannel
            }
        }.build()

        val responseChunks = mutableListOf<String>()
        val callJob = launch {
            val call = client.call(request)
            val response = call.response

            assertEquals(HttpStatusCode.OK, response.status)

            val channel = (response.body.toByteStream() as? aws.smithy.kotlin.runtime.content.ByteStream.ChannelStream)?.readFrom()!!
            val buffer = SdkBuffer()
            while (!channel.isClosedForRead) {
                val bytesRead = channel.read(buffer, 1024)
                if (bytesRead == -1L) break
                if (buffer.size > 0) {
                    responseChunks.add(buffer.readUtf8())
                }
            }
        }

        delay(100.milliseconds)
        requestChannel.write(SdkBuffer().apply { write("req1\n".encodeToByteArray()) })
        requestChannel.flush()

        delay(100.milliseconds)
        requestChannel.write(SdkBuffer().apply { write("req2\n".encodeToByteArray()) })
        requestChannel.flush()

        delay(100.milliseconds)
        requestChannel.close()

        callJob.join()

        assertEquals("resp1\nresp2\nresp3\n", responseChunks.joinToString(""))

        val recordedRequest = server.takeRequest()
        assertNotNull(recordedRequest)
        assertEquals("HTTP/2", recordedRequest.version)
        assertEquals("req1\nreq1\n", recordedRequest.body?.utf8())
    }

    @Test
    fun testHttp2PseudoHeaders() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("OK")
                .addHeader("x-custom-header", "custom-value")
                .build(),
        )

        val request = HttpRequestBuilder().apply {
            method = HttpMethod.GET
            url {
                scheme = Scheme.HTTPS
                host = Host.Domain("localhost")
                port = server.port
                path.encoded = "/test"
            }
        }.build()

        val call = client.call(request)
        val response = call.response

        assertEquals(HttpStatusCode.OK, response.status)

        response.headers.names().forEach { name ->
            assertTrue(!name.startsWith(":"), "Pseudo-header $name should be filtered")
        }

        assertEquals("custom-value", response.headers["x-custom-header"])

        val recordedRequest = server.takeRequest()
        assertNotNull(recordedRequest)
        assertEquals("HTTP/2", recordedRequest.version)
    }
}
