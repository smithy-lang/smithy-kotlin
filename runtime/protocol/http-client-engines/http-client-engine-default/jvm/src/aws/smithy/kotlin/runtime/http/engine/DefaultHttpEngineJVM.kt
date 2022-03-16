/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.engine.ktor.KtorEngine

internal actual fun newDefaultHttpEngine(config: HttpClientEngineConfig): HttpClientEngine = KtorEngine(config)
