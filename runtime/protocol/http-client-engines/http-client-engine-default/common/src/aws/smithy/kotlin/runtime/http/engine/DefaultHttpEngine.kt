/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine

/**
 * Factory function to create a new HTTP client engine using the default for the current KMP target
 */
public fun DefaultHttpEngine(block: (HttpClientEngineConfig.Builder.() -> Unit)? = null): HttpClientEngine =
    newDefaultHttpEngine(block)

internal expect fun newDefaultHttpEngine(block: (HttpClientEngineConfig.Builder.() -> Unit)?): HttpClientEngine
