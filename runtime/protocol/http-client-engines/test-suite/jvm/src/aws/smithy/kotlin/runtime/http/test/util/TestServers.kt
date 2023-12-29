/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.test.suite.*
import aws.smithy.kotlin.runtime.http.test.suite.concurrentTests
import aws.smithy.kotlin.runtime.http.test.suite.downloadTests
import aws.smithy.kotlin.runtime.http.test.suite.tlsTests
import aws.smithy.kotlin.runtime.http.test.suite.uploadTests
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import redirectTests
import java.io.Closeable
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

private data class TestServer(
    val port: Int,
    val type: ConnectorType,
    val protocolName: String?,
    val initializer: Application.() -> Unit,
)

private fun testServer(serverType: ServerType): TestServer = when (serverType) {
    ServerType.DEFAULT -> TestServer(8082, ConnectorType.HTTP, null, Application::testRoutes)

    // FIXME Enable once we figure out how to get TLS1 and TLS1.1 working
    // ServerType.TLS_1_0 -> TestServer(8090, ConnectorType.HTTPS, "TLSv1", Application::tlsRoutes)

    ServerType.TLS_1_1 -> TestServer(8091, ConnectorType.HTTPS, "TLSv1.1", Application::tlsRoutes)
    ServerType.TLS_1_2 -> TestServer(8092, ConnectorType.HTTPS, "TLSv1.2", Application::tlsRoutes)
    ServerType.TLS_1_3 -> TestServer(8093, ConnectorType.HTTPS, "TLSv1.3", Application::tlsRoutes)
}

private class Resources : Closeable {
    private val resources = mutableListOf<Closeable>()

    fun add(resource: Closeable) {
        resources.add(resource)
    }

    override fun close() {
        resources.forEach(Closeable::close)
    }

    val size: Int get() = resources.size
}

/**
 * Entry point used by Gradle to start up the shared local test server
 * @param sslConfigPath The path at which to write the generated SSL config
 */
internal fun startServers(sslConfigPath: String): Closeable {
    val sslConfig = SslConfig.generate()
    println("Persisting custom SSL config to $sslConfigPath...")
    sslConfig.persist(Paths.get(sslConfigPath))

    val servers = Resources()
    println("Starting local servers for HTTP client engine test suite...")
    println("Setting JKS path ${sslConfig.keyStoreFile.absolutePath}")

    try {
        ServerType
            .entries
            .map(::testServer)
            .forEach { testServer ->
                val runningInstance = tlsServer(testServer, sslConfig)
                servers.add {
                    println("Stopping server on port ${testServer.port}...")
                    runningInstance.stop(0L, 0L, TimeUnit.MILLISECONDS)
                }
            }

        // ensure servers are up and listening before tests run
        Thread.sleep(1000)
    } catch (e: Exception) {
        println("Encountered error while starting servers: $e")
        servers.close()
        throw e
    }

    println("...all ${servers.size} servers started!")

    return servers
}

private fun tlsServer(instance: TestServer, sslConfig: SslConfig): ApplicationEngine {
    val description = "${instance.type.name} server on port ${instance.port}"
    println("Starting $description...")
    val environment = applicationEngineEnvironment {
        when (instance.type) {
            ConnectorType.HTTP -> connector { port = instance.port }

            ConnectorType.HTTPS -> sslConnector(
                keyStore = sslConfig.keyStore,
                keyAlias = sslConfig.certificateAlias,
                keyStorePassword = sslConfig.keyStorePassword::toCharArray,
                privateKeyPassword = sslConfig.certificatePassword::toCharArray,
            ) {
                port = instance.port
                keyStorePath = sslConfig.keyStoreFile
                enabledProtocols = instance.protocolName?.let(::listOf)
            }
        }

        modules.add(instance.initializer)
    }
    return try {
        embeddedServer(Jetty, environment).start()
    } catch (e: Exception) {
        println("$description failed to start with exception $e")
        throw e
    }
}

// configure the test server routes
internal fun Application.testRoutes() {
    redirectTests()
    downloadTests()
    uploadTests()
    concurrentTests()
    nonAsciiHeaderValueTests()
}

// configure SSL-only routes
internal fun Application.tlsRoutes() {
    tlsTests()
}
