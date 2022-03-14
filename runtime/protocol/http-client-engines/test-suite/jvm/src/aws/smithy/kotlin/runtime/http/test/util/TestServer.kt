/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.test.suite.downloadTests
import aws.smithy.kotlin.runtime.http.test.suite.uploadTests
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import redirectTests
import java.io.Closeable
import java.util.concurrent.TimeUnit

private const val DEFAULT_PORT = 8082

private class Resources : Closeable {
    private val resources = mutableListOf<Closeable>()

    fun add(resource: Closeable) {
        resources.add(resource)
    }

    override fun close() {
        resources.forEach(Closeable::close)
    }
}

/**
 * Entry point used by Gradle to startup the shared local test server
 */
internal fun startServer(): Closeable {
    val servers = Resources()
    println("starting local server for HTTP client engine test suite")

    try {
        val server = embeddedServer(CIO, DEFAULT_PORT) {
            testRoutes()
        }.start()

        servers.add(Closeable { server.stop(0L, 0L, TimeUnit.MILLISECONDS) })

        // ensure server is up and listening before tests run
        Thread.sleep(500)
    } catch (ex: Exception) {
        servers.close()
    }

    return servers
}

// configure the test server routes
internal fun Application.testRoutes() {
    redirectTests()
    downloadTests()
    uploadTests()

    routing {
        post("/echo") {
            val response = call.receiveText()
            call.respond(response)
        }
    }
}
