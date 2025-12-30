/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.httptest.TestWithLocalServer
import aws.smithy.kotlin.runtime.io.use
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class AsyncStressTest : TestWithLocalServer() {
    override val server = embeddedServer(
        CIO,
        configure = {
            connector {
                port = serverPort
            }

            connectionIdleTimeoutSeconds = 60
        },
    ) {
        routing {
            get("/largeResponse") {
                // something that fills the stream window...
                val respSize = DEFAULT_WINDOW_SIZE_BYTES * 2
                val text = "testing"
                call.respondText(text.repeat(respSize / text.length))
            }
        }
    }.start()

    @Test
    fun testStreamNotConsumed() = runBlocking {
        // test that filling the stream window and not consuming the body stream still cleans up resources
        // appropriately and allows requests to proceed (a stream that isn't consumed will be in a stuck state
        // if the window is full and never incremented again, this can lead to all connections being consumed
        // and the engine to no longer make further requests)

        val taskStatuses = List(1_000) { TaskStatus() }

        try {
            CrtHttpEngine {
                maxConnections = 1_001u
                maxConcurrency = 1_001u
            }.use { engine ->
                val client = SdkHttpClient(engine)
                val request = HttpRequestBuilder().apply {
                    url {
                        scheme = Scheme.HTTP
                        method = HttpMethod.GET
                        host = Host.Domain(testHost)
                        port = serverPort
                        path.decoded = "/largeResponse"
                    }
                }

                // Sanity check: verify that a single request goes through
                val sanityCall = client.call(request)
                val resp = sanityCall.response.body.readAll()
                sanityCall.complete()
                println("Successfully completed sanity check call (response length = ${resp?.size})")

                withTimeout(30.seconds) {
                    taskStatuses.forEachIndexed { idx, status ->
                        async {
                            status.taskStarted = true
                            try {
                                val call = client.call(request)
                                status.callStarted = true
                                yield()
                                status.yieldCompleted = true
                                call.complete()
                                status.callCompleted = true
                            } catch (ex: Throwable) {
                                status.exception = ex

                                if (ex !is CancellationException) {
                                    println("exception on $idx: $ex")
                                }

                                throw ex
                            }
                        }
                        yield()
                    }
                }
            }
        } finally {
            var tasksStarted = 0
            var callsStarted = 0
            var yieldsCompleted = 0
            var callsCompleted = 0
            var cancelExceptions = 0
            var otherExceptions = 0
            val exceptionMessages = mutableSetOf<String>()

            taskStatuses.forEach { status ->
                if (status.taskStarted) tasksStarted++
                if (status.callStarted) callsStarted++
                if (status.yieldCompleted) yieldsCompleted++
                if (status.callCompleted) callsCompleted++

                val ex = status.exception
                if (ex != null) {
                    if (ex is CancellationException) {
                        cancelExceptions++
                    } else {
                        otherExceptions++
                        exceptionMessages.add(ex.message ?: "(no message)")
                    }
                }
            }

            println("Tasks started           : $tasksStarted")
            println("Calls started           : $callsStarted")
            println("Yields completed        : $yieldsCompleted")
            println("Calls completed         : $callsCompleted")
            println("Cancellation exceptions : $cancelExceptions")
            println("Other exceptions        : $otherExceptions")
            exceptionMessages.forEach { println("• \"$it\"") }
        }
    }
}

private data class TaskStatus(
    var taskStarted: Boolean = false,
    var callStarted: Boolean = false,
    var yieldCompleted: Boolean = false,
    var callCompleted: Boolean = false,
    var exception: Throwable? = null,
)
