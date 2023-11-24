/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.engine.DefaultHttpEngine
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine

internal actual fun engineFactories(): List<TestEngineFactory> =
    listOf(
        TestEngineFactory("DefaultHttpEngine", ::DefaultHttpEngine),
        TestEngineFactory("CrtHttpEngine") { CrtHttpEngine(it) },
    )
