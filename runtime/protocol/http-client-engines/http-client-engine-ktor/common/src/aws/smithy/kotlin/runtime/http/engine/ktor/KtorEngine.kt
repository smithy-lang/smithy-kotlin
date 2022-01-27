/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig

/**
 * Specifies the ktor http client of which platform specific [HttpClientEngine]'s actualize
 *
 * @param config Provides configuration for the DefaultHttpClientEngine
 */
expect class KtorEngine(config: HttpClientEngineConfig) : HttpClientEngineBase {
    val config: HttpClientEngineConfig
}
