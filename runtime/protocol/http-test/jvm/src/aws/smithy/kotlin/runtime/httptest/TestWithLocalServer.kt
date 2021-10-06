/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.logging.Logger
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.*
import java.util.concurrent.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Spin up a local server using ktor-server to test real requests against. This can used in integration tests where
 * mocking an HTTP client engine is difficult.
 */
public abstract class TestWithLocalServer {
    protected val serverPort: Int = ServerSocket(0).use { it.localPort }
    protected val testHost: String = "localhost"

    public abstract val server: ApplicationEngine

    private val logger = Logger.getLogger<TestWithLocalServer>()

    @OptIn(ExperimentalTime::class)
    @BeforeTest
    public fun startServer() = runBlocking {
        withTimeout(Duration.seconds(5)) {
            var attempt = 0

            do {
                attempt++
                try {
                    server.start()
                    logger.info { "test server listening on: $testHost:$serverPort" }
                    break
                } catch (cause: Throwable) {
                    if (attempt >= 10) throw cause
                    Thread.sleep(250L * attempt)
                }
            } while (true)

            ensureServerRunning()
        }
    }

    @AfterTest
    public fun stopServer() {
        server.stop(0, 0, TimeUnit.SECONDS)
        logger.info { "test server stopped" }
    }

    private fun ensureServerRunning() {
        do {
            try {
                Socket("localhost", serverPort).close()
                break
            } catch (_: Throwable) {
                Thread.sleep(100)
            }
        } while (true)
    }
}
