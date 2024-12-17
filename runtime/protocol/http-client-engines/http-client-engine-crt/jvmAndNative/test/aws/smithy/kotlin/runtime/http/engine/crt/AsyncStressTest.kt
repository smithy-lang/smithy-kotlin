/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.httptest.TestWithLocalServer
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class AsyncStressTest : TestWithLocalServer() {
    override val server = embeddedServer(CIO, serverPort) {
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
        val client = SdkHttpClient(CrtHttpEngine())
        val request = HttpRequestBuilder().apply {
            url {
                scheme = Scheme.HTTP
                method = HttpMethod.GET
                host = Host.Domain(testHost)
                port = serverPort
                path.decoded = "/largeResponse"
            }
        }

        withTimeout(10.seconds) {
            repeat(1_000) {
                async {
                    try {
                        val call = client.call(request)
                        yield()
                        call.complete()
                    } catch (ex: Exception) {
                        println("exception on $it: $ex")
                        throw ex
                    }
                }
                yield()
            }
        }
    }
}
