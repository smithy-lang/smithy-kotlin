/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.engineConfig
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.http.test.util.testSetup
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

        test { env, client ->
            val req = HttpRequest {
                testSetup(env)
                url.path = "concurrent"
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

        test { env, client ->
            val req = HttpRequest {
                testSetup(env)
                url.path = "concurrent"
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
}
