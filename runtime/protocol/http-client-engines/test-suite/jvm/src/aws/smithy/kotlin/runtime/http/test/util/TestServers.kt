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
import io.ktor.server.jetty.jakarta.Jetty
import io.ktor.server.jetty.jakarta.JettyApplicationEngineBase
import redirectTests
import java.io.Closeable
import java.net.ServerSocket
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

private data class TestServer(
    val type: ConnectorType,
    val protocolName: String?,
    val initializer: Application.() -> Unit,
    val port: Int = ServerSocket(0).use { it.localPort },
)

private fun testServer(serverType: ServerType): TestServer = when (serverType) {
    ServerType.DEFAULT -> TestServer(ConnectorType.HTTP, null, Application::testRoutes)

    // FIXME Enable once we figure out how to get TLS1 and TLS1.1 working
    // ServerType.TLS_1_0 -> TestServer(ConnectorType.HTTPS, "TLSv1", Application::tlsRoutes)

    ServerType.TLS_1_1 -> TestServer(ConnectorType.HTTPS, "TLSv1.1", Application::tlsRoutes)
    ServerType.TLS_1_2 -> TestServer(ConnectorType.HTTPS, "TLSv1.2", Application::tlsRoutes)
    ServerType.TLS_1_3 -> TestServer(ConnectorType.HTTPS, "TLSv1.3", Application::tlsRoutes)
}

private class Resources : Closeable {
    private val resources = mutableListOf<Closeable>()
    private val filesToDelete = mutableListOf<Path>()

    fun add(resource: Closeable) {
        resources.add(resource)
    }

    fun addFileToDelete(file: Path) {
        filesToDelete.add(file)
    }

    override fun close() {
        resources.forEach(Closeable::close)
        filesToDelete.forEach { file ->
            try {
                if (file.exists()) {
                    file.toFile().delete()
                    println("Deleted file $file")
                }
            } catch (e: Exception) {
                println("Failed to delete file $file: ${e.message}")
            }
        }
    }

    val size: Int get() = resources.size
}

/**
 * Entry point used by Gradle to start up the shared local test server
 * @param sslConfigPath The path at which to write the generated SSL config
 */
internal fun startServers(sslConfigPath: String): Closeable {
    val servers = Resources()

    val sslConfig = SslConfig.generate()
    println("Persisting custom SSL config to $sslConfigPath...")
    sslConfig.persist(Paths.get(sslConfigPath))
    servers.addFileToDelete(Paths.get(sslConfigPath))

    println("Starting local servers for HTTP client engine test suite...")
    println("Setting JKS path ${sslConfig.keyStoreFile.absolutePath}")

    try {
        val testServers = ServerType.entries.associateWith(::testServer)
        testServers.values.forEach { testServer ->
            val runningInstance = tlsServer(testServer, sslConfig)
            servers.add {
                println("Stopping server on port ${testServer.port}...")
                runningInstance.stop(0L, 0L, TimeUnit.MILLISECONDS)
            }
        }

        val portsConfigPath = Paths.get(sslConfigPath).parent.resolve("test-server-ports.properties")
        println("Persisting test servers port configuration to $portsConfigPath...")
        persistPortConfig(testServers, portsConfigPath)
        servers.addFileToDelete(portsConfigPath)

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

private fun persistPortConfig(testServers: Map<ServerType, TestServer>, path: java.nio.file.Path) {
    val properties = java.util.Properties()

    testServers.forEach { (serverType, testServer) ->
        val protocol = if (testServer.type == ConnectorType.HTTPS) {
            "https"
        } else {
            "http"
        }
        properties.setProperty(serverType.name, "$protocol://127.0.0.1:${testServer.port}")
    }

    path.toFile().outputStream().use { output ->
        properties.store(output, "Test server port configuration")
    }
}

private fun tlsServer(instance: TestServer, sslConfig: SslConfig): EmbeddedServer<*, *> {
    val description = "${instance.type.name} server on port ${instance.port}"
    println("Starting $description...")
    val rootConfig = serverConfig {
        module(instance.initializer)
    }
    val engineConfig: JettyApplicationEngineBase.Configuration.() -> Unit = {
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

        idleTimeout = 3.seconds // Required for ConnectionTest.testShortLivedConnections
    }

    return try {
        embeddedServer(Jetty, rootConfig, engineConfig).start()
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
    headerTests()
    connectionTests()
}

// configure SSL-only routes
internal fun Application.tlsRoutes() {
    tlsTests()
}
