/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.Protocol
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import aws.smithy.kotlin.runtime.httptest.TestWithLocalServer
import aws.smithy.kotlin.runtime.testing.IgnoreWindows
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
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
    }

    @IgnoreWindows("FIXME - times out after upgrade to kotlinx.coroutines 1.6.0")
    @Test
    fun testStreamNotConsumed() = runBlocking {
        // test that filling the stream window and not consuming the body stream still cleans up resources
        // appropriately and allows requests to proceed (a stream that isn't consumed will be in a stuck state
        // if the window is full and never incremented again, this can lead to all connections being consumed
        // and the engine to no longer make further requests)
        val client = sdkHttpClient(CrtHttpEngine())
        val request = HttpRequestBuilder().apply {
            url {
                scheme = Protocol.HTTP
                method = HttpMethod.GET
                host = testHost
                port = serverPort
                path = "/largeResponse"
            }
        }

        withTimeout(5.seconds) {
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
