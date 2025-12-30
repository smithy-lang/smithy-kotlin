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
import kotlinx.atomicfu.atomic
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

        val tasksStarted = atomic(0)
        val callsStarted = atomic(0)
        val yieldsCompleted = atomic(0)
        val callsCompleted = atomic(0)
        val cancelExceptions = atomic(0)
        val nonCancelExceptions = atomic(0)

        try {
            CrtHttpEngine().use { engine ->
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
                println("Successfully completed sanity check call (response length = ${resp?.size}")

                withTimeout(30.seconds) {
                    repeat(1_000) {
                        async {
                            tasksStarted.incrementAndGet()
                            try {
                                val call = client.call(request)
                                callsStarted.incrementAndGet()
                                yield()
                                yieldsCompleted.incrementAndGet()
                                call.complete()
                                callsCompleted.incrementAndGet()
                            } catch (_: CancellationException) {
                                // expected
                                cancelExceptions.incrementAndGet()
                            } catch (ex: Exception) {
                                nonCancelExceptions.incrementAndGet()
                                println("exception on $it: $ex")
                                throw ex
                            }
                        }
                        yield()
                    }
                }
            }
        } finally {
            println("Tasks started: $tasksStarted")
            println("Calls started: $callsStarted")
            println("Yields completed: $yieldsCompleted")
            println("Calls completed: $callsCompleted")
            println("CancellationExceptions: $cancelExceptions")
            println("Other exceptions: $nonCancelExceptions")
        }
    }
}
