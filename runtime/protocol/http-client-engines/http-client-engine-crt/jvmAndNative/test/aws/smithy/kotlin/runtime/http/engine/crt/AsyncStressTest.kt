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
import aws.smithy.kotlin.runtime.io.use
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
import kotlin.time.Duration
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

    private fun testStreamNotConsumedImpl(timeout: Duration = 10.seconds, concurrency: Int = 1000) = runBlocking {
        // test that filling the stream window and not consuming the body stream still cleans up resources
        // appropriately and allows requests to proceed (a stream that isn't consumed will be in a stuck state
        // if the window is full and never incremented again, this can lead to all connections being consumed
        // and the engine to no longer make further requests)
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

            withTimeout(timeout) {
                repeat(concurrency) {
                    async {
                        try {
                            val call = client.call(request)
                            yield()
                            call.complete()
                        } catch (ex: Exception) {
                            throw ex
                        }
                    }
                    yield()
                }
            }
        }
    }

    @Test fun testStreamNotConsumed() = testStreamNotConsumedImpl()
    @Test fun testStreamNotConsumed_x100() = testStreamNotConsumedImpl(concurrency = 100)
    @Test fun testStreamNotConsumed_x10() = testStreamNotConsumedImpl(concurrency = 10)
    @Test fun testStreamNotConsumed_30s() = testStreamNotConsumedImpl(timeout = 30.seconds)
    @Test fun testStreamNotConsumed_60s() = testStreamNotConsumedImpl(timeout = 60.seconds)
}
