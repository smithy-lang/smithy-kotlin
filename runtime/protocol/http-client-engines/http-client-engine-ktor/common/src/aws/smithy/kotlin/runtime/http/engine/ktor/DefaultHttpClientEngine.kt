/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.http.engine.*

/**
 * Specifies the common default http client of which platform specific [HttpClientEngine]'s actualize
 *
 * @param config Provides configuration for the DefaultHttpClientEngine
 */
expect class DefaultHttpClientEngine(config: HttpClientEngineConfig = HttpClientEngineConfig.Default) : HttpClientEngineBase
