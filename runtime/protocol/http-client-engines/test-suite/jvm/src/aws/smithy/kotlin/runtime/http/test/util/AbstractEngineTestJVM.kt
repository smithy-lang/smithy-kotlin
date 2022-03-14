/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.ktor.KtorEngine
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

actual abstract class AbstractEngineTest actual constructor() {
    actual fun testEngines(block: EngineTestBuilder.() -> Unit) {
        engines().forEach { engine ->
            val client = sdkHttpClient(engine)
            testWithClient(client, block = block)
        }
    }

    private fun engines(): List<HttpClientEngine> = listOf(
        KtorEngine()
    )
}

actual fun runBlockingTest(
    context: CoroutineContext,
    timeout: Duration?,
    block: suspend CoroutineScope.() -> Unit
) {
    runBlocking(context) {
        if (timeout != null) {
            withTimeout(timeout) {
                block()
            }
        } else {
            block()
        }
    }
}
