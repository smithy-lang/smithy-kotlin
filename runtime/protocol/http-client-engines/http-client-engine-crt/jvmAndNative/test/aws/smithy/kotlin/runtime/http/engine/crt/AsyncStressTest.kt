/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.http.*
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @Test fun testStreamNotConsumed_2() = testStreamNotConsumed()
    @Test fun testStreamNotConsumed_3() = testStreamNotConsumed()
    @Test fun testStreamNotConsumed_4() = testStreamNotConsumed()
    @Test fun testStreamNotConsumed_5() = testStreamNotConsumed()
    @Test fun testStreamNotConsumed_6() = testStreamNotConsumed()
    @Test fun testStreamNotConsumed_7() = testStreamNotConsumed()
    @Test fun testStreamNotConsumed_8() = testStreamNotConsumed()
    @Test fun testStreamNotConsumed_9() = testStreamNotConsumed()
    @Test fun testStreamNotConsumed_10() = testStreamNotConsumed()

    @Test
    fun testStreamNotConsumed() = runBlocking {
        // test that filling the stream window and not consuming the body stream still cleans up resources
        // appropriately and allows requests to proceed (a stream that isn't consumed will be in a stuck state
        // if the window is full and never incremented again, this can lead to all connections being consumed
        // and the engine to no longer make further requests)

        val tasks = List(1_000) { StreamTask() }

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
                    tasks.forEachIndexed { idx, task ->
                        async {
                            task.taskStarted = true
                            try {
                                val call = client.call(request)
                                task.call = call

                                call.complete()
                                task.callCompleted = true
                            } catch (ex: Throwable) {
                                task.exception = ex

                                if (ex !is CancellationException) {
                                    println("exception on $idx: $ex")
                                }

                                throw ex
                            }
                        }
                    }
                }
            }
        } finally {
            var tasksStarted = 0
            var callsStarted = 0
            var callsCompleted = 0
            var cancelExceptions = 0
            var otherExceptions = 0
            val exceptionMessages = mutableSetOf<String>()

            tasks.forEach { task ->
                if (task.taskStarted) tasksStarted++
                if (task.call != null) callsStarted++
                if (task.callCompleted) callsCompleted++

                val ex = task.exception
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
            println("Calls completed         : $callsCompleted")
            println("Cancellation exceptions : $cancelExceptions")
            println("Other exceptions        : $otherExceptions")
            exceptionMessages.forEach { println("• \"$it\"") }
        }
    }
}

private data class StreamTask(
    var taskStarted: Boolean = false,
    var call: HttpCall? = null,
    var callCompleted: Boolean = false,
    var exception: Throwable? = null,
)
