/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.engine.DefaultHttpEngine
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

internal actual fun engineFactories(): List<TestEngineFactory> =
    listOf(
        TestEngineFactory("DefaultHttpEngine") { DefaultHttpEngine(it) },
        TestEngineFactory("CrtHttpEngine") { CrtHttpEngine(it) },
        TestEngineFactory("KtorEngine") { KtorOkHttpEngine(it) }
    )

internal actual fun runBlockingTest(
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
