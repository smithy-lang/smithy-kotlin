/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.engine.DefaultHttpEngine
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine
import aws.smithy.kotlin.runtime.http.engine.okhttp4.OkHttp4Engine
import aws.smithy.kotlin.runtime.net.url.Url
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.exists

internal actual fun engineFactories(): List<TestEngineFactory> = // FIXME Move DefaultHttpEngine and CrtHttpEngine to `jvmAndNative`
    listOf(
        TestEngineFactory("DefaultHttpEngine", ::DefaultHttpEngine),
        TestEngineFactory("CrtHttpEngine") { CrtHttpEngine(it) },
        TestEngineFactory("OkHttp4Engine") { OkHttp4Engine(it) },
    )

private fun loadTestServerPorts(): Map<ServerType, Url> {
    val sslConfigPath = System.getProperty("SSL_CONFIG_PATH")
    val portsConfigPath = Paths.get(sslConfigPath).parent.resolve("test-server-ports.properties")
    check(portsConfigPath.exists()) { "Failed to find ports configuration at $portsConfigPath" }
    val properties = Properties()
    portsConfigPath.toFile().inputStream().use { input ->
        properties.load(input)
    }

    return ServerType.entries.mapNotNull { serverType ->
        properties.getProperty(serverType.name)?.let { url ->
            serverType to Url.parse(url)
        }
    }.toMap()
}

internal actual val testServers = loadTestServerPorts()
