/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine

internal actual fun newDefaultHttpEngine(block: (HttpClientEngineConfig.Builder.() -> Unit)?): HttpClientEngine = if (block != null) {
    OkHttpEngine(block)
} else {
    OkHttpEngine()
}
