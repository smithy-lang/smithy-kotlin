/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

// FIXME Add wasmJs engines to test
internal actual fun engineFactories(): List<TestEngineFactory> = listOf()

// FIXME Add servers to test
internal actual val testServers: Map<ServerType, Url> = mapOf()

internal actual fun runBlockingTest(
    context: CoroutineContext,
    timeout: Duration?,
    block: suspend CoroutineScope.() -> Unit,
): Unit = TODO("Not yet implemented on wasmJs: blocking")
