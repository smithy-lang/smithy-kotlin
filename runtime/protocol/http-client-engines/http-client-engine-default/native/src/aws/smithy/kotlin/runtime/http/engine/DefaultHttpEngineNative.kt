/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine

internal actual fun newDefaultHttpEngine(block: (HttpClientEngineConfig.Builder.() -> Unit)?): CloseableHttpClientEngine = CrtHttpEngine()
