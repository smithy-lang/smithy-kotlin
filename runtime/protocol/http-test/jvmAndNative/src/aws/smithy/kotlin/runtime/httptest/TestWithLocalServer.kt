/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.httptest

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.server.engine.EmbeddedServer
import io.ktor.utils.io.core.use
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration.Companion.seconds

/**
 * Spin up a local server using ktor-server to test real requests against. This can used in integration tests where
 * mocking an HTTP client engine is difficult.
 */
public abstract class TestWithLocalServer {
    protected val serverPort: Int
        get() = runBlocking {
            SelectorManager(this.coroutineContext).use {
                aSocket(it)
                    .tcp()
                    .bind()
                    .use { (it.localAddress as InetSocketAddress).port }
            }
        }

    protected val testHost: String = "localhost"

    public abstract val server: EmbeddedServer<*, *>

    @BeforeTest
    public fun startServer(): Unit = runBlocking {
        withTimeout(5.seconds) {
            var attempt = 0

            do {
                attempt++
                try {
                    server.start()
                    break
                } catch (cause: Throwable) {
                    if (attempt >= 10) throw cause
                    delay(250L * attempt)
                }
            } while (true)

            ensureServerRunning()
        }
    }

    @AfterTest
    public fun stopServer() {
        server.stop(0, 0)
        println("test server stopped")
    }

    private fun ensureServerRunning() = runBlocking {
        val client = HttpClient()
        val url = "http://$testHost:$serverPort"
        try {
            while (true) {
                try {
                    val response = client.get(url)
                    if (response.status == HttpStatusCode.OK) break
                } catch (_: Exception) {
                    delay(100)
                }
            }
        } finally {
            client.close()
        }
    }
}
