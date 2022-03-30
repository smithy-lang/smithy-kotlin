/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine

/**
 * Factory function to create a new HTTP client engine using the default for the current KMP target
 */
fun DefaultHttpEngine(config: HttpClientEngineConfig = HttpClientEngineConfig.Default): HttpClientEngine =
    newDefaultHttpEngine(config)

fun DefaultHttpEngine(block: HttpClientEngineConfig.Builder.() -> Unit): HttpClientEngine {
    val builder = HttpClientEngineConfig.Builder().apply(block)
    val config = HttpClientEngineConfig(builder)
    return DefaultHttpEngine(config)
}

internal expect fun newDefaultHttpEngine(config: HttpClientEngineConfig): HttpClientEngine
