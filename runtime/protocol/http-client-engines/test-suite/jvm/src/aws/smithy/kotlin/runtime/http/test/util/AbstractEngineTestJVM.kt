/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.engine.DefaultHttpEngine
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine
import aws.smithy.kotlin.runtime.http.engine.okhttp4.OkHttp4Engine
import aws.smithy.kotlin.runtime.net.url.Url

internal actual fun engineFactories(): List<TestEngineFactory> =
    // FIXME Move DefaultHttpEngine and CrtHttpEngine to `jvmAndNative`
    listOf(
        TestEngineFactory("DefaultHttpEngine", ::DefaultHttpEngine),
        TestEngineFactory("CrtHttpEngine") { CrtHttpEngine(it) },
        TestEngineFactory("OkHttp4Engine") { OkHttp4Engine(it) },
    )

internal actual val testServers = mapOf(
    ServerType.DEFAULT to Url.parse("http://127.0.0.1:8082"),

    // FIXME Enable once we figure out how to get TLS1 and TLS1.1 working
    // ServerType.TLS_1_0 to Url.parse("https://127.0.0.1:8090"),

    ServerType.TLS_1_1 to Url.parse("https://127.0.0.1:8091"),
    ServerType.TLS_1_2 to Url.parse("https://127.0.0.1:8092"),
    ServerType.TLS_1_3 to Url.parse("https://127.0.0.1:8093"),
)
