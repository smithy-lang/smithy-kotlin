/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceEngineFactoryTest {
    val closeGracePeriodMillis = TestParams.CLOSE_GRACE_PERIOD_MILLIS
    val closeTimeoutMillis = TestParams.CLOSE_TIMEOUT_MILLIS
    val gracefulWindow = TestParams.GRACEFUL_WINDOW
    val requestBodyLimit = TestParams.REQUEST_BODY_LIMIT
    val portListenerTimeout = TestParams.PORT_LISTENER_TIMEOUT

    val projectDir: Path = Paths.get("build/service-cbor-test")

    @Test
    fun `checks service with netty engine`() {
        val nettyPort: Int = ServerSocket(0).use { it.localPort }
        val nettyProc = startService("netty", nettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(nettyPort, portListenerTimeout)
        assertTrue(ready, "Service did not start within $portListenerTimeout s")
        cleanupService(nettyProc, gracefulWindow)
    }

    @Test
    fun `checks service with cio engine`() {
        val cioPort: Int = ServerSocket(0).use { it.localPort }
        val cioProc = startService("cio", cioPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(cioPort, portListenerTimeout)
        assertTrue(ready, "Service did not start within $portListenerTimeout s")
        cleanupService(cioProc, gracefulWindow)
    }

    @Test
    fun `checks service with jetty jakarta engine`() {
        val jettyPort: Int = ServerSocket(0).use { it.localPort }
        val jettyProc = startService("jetty-jakarta", jettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(jettyPort, portListenerTimeout)
        assertTrue(ready, "Service did not start within $portListenerTimeout s")
        cleanupService(jettyProc, gracefulWindow)
    }
}
