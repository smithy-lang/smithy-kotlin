/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.httptest

import io.ktor.client.request.get
import io.ktor.server.engine.EmbeddedServer
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
    protected val serverPort: Int = 54734
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
        }
    }

    @AfterTest
    public fun stopServer() {
        server.stop(0, 0)
        println("test server stopped")
    }
}
