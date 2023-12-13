/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.engine.DefaultHttpEngine
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine
import aws.smithy.kotlin.runtime.net.url.Url

internal actual fun engineFactories(): List<TestEngineFactory> =
    listOf(
        TestEngineFactory("DefaultHttpEngine", ::DefaultHttpEngine),
        TestEngineFactory("CrtHttpEngine") { CrtHttpEngine(it) },
    )

internal actual val testServers = mapOf(
    ServerType.DEFAULT to Url.parse("http://127.0.0.1:8082"),

    // FIXME Enable once we figure out how to get TLS1 and TLS1.1 working
    // ServerType.TLS_1_0 to Url.parse("https://127.0.0.1:8083"),

    ServerType.TLS_1_1 to Url.parse("https://127.0.0.1:8084"),
    ServerType.TLS_1_2 to Url.parse("https://127.0.0.1:8085"),
    ServerType.TLS_1_3 to Url.parse("https://127.0.0.1:8086"),
)
