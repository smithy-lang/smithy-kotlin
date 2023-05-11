/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.config.EngineFactory

/**
 * Specifies the platform-default HTTP engine. Different platforms (e.g., Java) will use different implementations
 * (e.g., OkHttp).
 */
public object DefaultHttpEngine : EngineFactory<HttpClientEngineConfig.Builder, HttpClientEngine> {
    override val engineConstructor: (HttpClientEngineConfig.Builder.() -> Unit) -> HttpClientEngine =
        ::DefaultHttpEngine
}

/**
 * Factory function to create a new HTTP client engine using the default for the current KMP target
 */
public fun DefaultHttpEngine(block: (HttpClientEngineConfig.Builder.() -> Unit)? = null): CloseableHttpClientEngine =
    newDefaultHttpEngine(block)

internal expect fun newDefaultHttpEngine(block: (HttpClientEngineConfig.Builder.() -> Unit)?): CloseableHttpClientEngine
