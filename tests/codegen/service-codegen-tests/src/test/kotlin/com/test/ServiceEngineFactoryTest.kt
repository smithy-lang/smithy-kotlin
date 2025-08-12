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
    val closeGracePeriodMillis: Long = 5_000L
    val closeTimeoutMillis: Long = 1_000L
    val gracefulWindow = closeTimeoutMillis + closeGracePeriodMillis
    val requestBodyLimit: Long = 10L * 1024 * 1024

    val portListnerTimeout = 60L
    val projectDir: Path = Paths.get("build/service-cbor-test")

    @Test
    fun `checks service with netty engine`() {
        val nettyPort: Int = ServerSocket(0).use { it.localPort }
        val nettyProc = startService("netty", nettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(nettyPort, portListnerTimeout)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
        cleanupService(nettyProc, gracefulWindow)
    }

    @Test
    fun `checks service with cio engine`() {
        val cioPort: Int = ServerSocket(0).use { it.localPort }
        val cioProc = startService("cio", cioPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(cioPort, portListnerTimeout)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
        cleanupService(cioProc, gracefulWindow)
    }

    @Test
    fun `checks service with jetty jakarta engine`() {
        val jettyPort: Int = ServerSocket(0).use { it.localPort }
        val jettyProc = startService("jetty-jakarta", jettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(jettyPort, portListnerTimeout)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
        cleanupService(jettyProc, gracefulWindow)
    }
}
