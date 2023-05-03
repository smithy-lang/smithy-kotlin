/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.test.suite.concurrentTests
import aws.smithy.kotlin.runtime.http.test.suite.downloadTests
import aws.smithy.kotlin.runtime.http.test.suite.uploadTests
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import redirectTests
import java.io.Closeable
import java.util.concurrent.TimeUnit

// TODO Finish once we have HTTP engine support for client certificates
/*
public const val keyStorePath: String = "build/keystore.jks"

public val keyStoreFile: File by lazy { File(keyStorePath) }

public val keyStore: KeyStore by lazy {
    val keyStore = buildKeyStore {
        certificate("TestServerCert") {
            password = "password"
            domains = listOf("127.0.0.1", "0.0.0.0", "localhost")
        }
    }
    keyStore.saveToFile(keyStoreFile, "password")

    keyStore
}
 */

public enum class TestServer(
    public val port: Int,
    public val type: ConnectorType,
    public val protocolName: String?,
    public val initializer: Application.() -> Unit,
) {
    Default(8082, ConnectorType.HTTP, null, Application::testRoutes),

    // TODO Finish once we have HTTP engine support for client certificates
    /*
    TlsV1(8083, ConnectorType.HTTPS, "TLSv1", Application::tlsRoutes),
    TlsV1_1(8084, ConnectorType.HTTPS, "TLSv1.1", Application::tlsRoutes),
    TlsV1_2(8085, ConnectorType.HTTPS, "TLSv1.2", Application::tlsRoutes),
    TlsV1_3(8086, ConnectorType.HTTPS, "TLSv1.3", Application::tlsRoutes),
    */
}

private class Resources : Closeable {
    private val resources = mutableListOf<Closeable>()

    fun add(resource: Closeable) {
        resources.add(resource)
    }

    override fun close() {
        resources.forEach(Closeable::close)
    }

    val size: Int = resources.size
}

/**
 * Entry point used by Gradle to startup the shared local test server
 */
internal fun startServers(): Closeable {
    val servers = Resources()
    println("Starting local servers for HTTP client engine test suite...")

    try {
        TestServer
            .values()
            .forEach { testServer ->
                val runningInstance = tlsServer(testServer)
                servers.add { runningInstance.stop(0L, 0L, TimeUnit.MILLISECONDS) }
            }

        // ensure servers are up and listening before tests run
        Thread.sleep(1000)
    } catch (ex: Exception) {
        servers.close()
        throw ex
    }

    println("...all ${servers.size} servers started!")

    return servers
}

private fun tlsServer(instance: TestServer): ApplicationEngine {
    val description = "${instance.type.name} server on port ${instance.port}"
    println("  Starting $description...")
    val environment = applicationEngineEnvironment {
        when (instance.type) {
            ConnectorType.HTTP -> connector { port = instance.port }

            // TODO Finish once we have HTTP engine support for client certificates
            /*
            ConnectorType.HTTPS -> sslConnector(
                keyStore = keyStore,
                keyAlias = keyStore.aliases().nextElement(),
                keyStorePassword = { "password".toCharArray() },
                privateKeyPassword = { "password".toCharArray() },
            ) {
                port = instance.port
                keyStorePath = keyStoreFile
                enabledProtocols = instance.protocolName?.let(::listOf)
            }
            */
        }
        modules.add(instance.initializer)
    }
    return try {
        embeddedServer(Jetty, environment).start()
    } catch (e: Exception) {
        println("  ...$description failed to start with exception $e")
        throw e
    }
}

// configure the test server routes
internal fun Application.testRoutes() {
    redirectTests()
    downloadTests()
    uploadTests()
    concurrentTests()
}

// TODO Finish once we have HTTP engine support for client certificates
/*
internal fun Application.tlsRoutes() {
    tlsTests()
}
*/
