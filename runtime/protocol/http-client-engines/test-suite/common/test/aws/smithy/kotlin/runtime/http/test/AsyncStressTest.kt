/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.test.util.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class AsyncStressTest : AbstractEngineTest() {

    @Test
    fun testConcurrentRequests() = testEngines {
        // https://github.com/awslabs/aws-sdk-kotlin/issues/170
        concurrency = 1_000
        engineConfig {
            // FIXME - investigate windows CI issue when this is removed
            maxConcurrency = 32u
        }

        test { _, client ->
            val req = HttpRequest {
                testSetup()
                url.path.decoded = "concurrent"
            }

            val call = client.call(req)
            assertEquals(HttpStatusCode.OK, call.response.status)

            try {
                val resp = call.response.body.readAll() ?: error("expected response body")

                val contentLength = call.response.body.contentLength ?: 0L
                val text = "testing"
                val expectedText = text.repeat(contentLength.toInt() / text.length)
                assertEquals(expectedText, resp.decodeToString())
            } finally {
                call.complete()
            }
        }
    }

    @Test
    fun testHttpCallJobCompletion() = testEngines {
        // https://github.com/awslabs/aws-sdk-kotlin/issues/587

        test { _, client ->
            val req = HttpRequest {
                testSetup()
                url.path.decoded = "concurrent"
            }

            val engineJobsBefore = client.engine.coroutineContext.job.children.toList()

            val call = client.call(req)
            assertEquals(HttpStatusCode.OK, call.response.status)

            val message = "engine=${client.engine}"
            assertTrue(call.coroutineContext.isActive, message)
            assertFalse(call.coroutineContext.job.isCompleted, message)
            val engineJobsDuring = client.engine.coroutineContext.job.children.toList()

            // any jobs needed to support request execution should be launched in the engine's scope
            // NOTE: callContext is launched in engine scope
            assertTrue(engineJobsDuring.size >= engineJobsBefore.size, message)

            call.complete()

            // NOTE: this is a bit finicky as engines are free to adapt requests as needed, including
            // launching coroutines. The completion of these coroutines may or may not have happened
            // at this point depending on the dispatcher used. This delay should block the current
            // thread and hopefully let any coroutines running on other dispatchers(threads) complete.
            // Running in debugger affects this though!
            delay(200.milliseconds)

            assertFalse(call.coroutineContext.isActive, message)
            assertTrue(call.coroutineContext.job.isCompleted, message)

            val engineJobsAfter = client.engine.coroutineContext.job.children.toList()
            assertEquals(engineJobsBefore.size, engineJobsAfter.size, message)
        }
    }

    @Test
    fun testJobCancellation() = testEngines {
        // https://github.com/smithy-lang/smithy-kotlin/issues/1061

        test { _, client ->
            val req = HttpRequest {
                testSetup()
                url.path.decoded = "slow"
            }

            // Expect CancellationException because we're cancelling
            assertFailsWith<CancellationException> {
                coroutineScope {
                    val parentScope = this

                    println("Invoking call on ctx $coroutineContext")
                    val call = client.call(req)

                    val bytes = async {
                        delay(100.milliseconds)
                        println("Body of type ${call.response.body} on ctx $coroutineContext")
                        try {
                            call.response.body.readAll()
                        } catch (e: Throwable) {
                            // IllegalStateException: "Unbalanced enter/exit" will be thrown if body closed improperly
                            assertIsNot<IllegalStateException>(e)
                            null
                        }
                    }

                    val cancellation = async {
                        delay(400.milliseconds)
                        parentScope.cancel("Cancelling!")
                    }

                    awaitAll(bytes, cancellation)
                }
            }
        }
    }
}
