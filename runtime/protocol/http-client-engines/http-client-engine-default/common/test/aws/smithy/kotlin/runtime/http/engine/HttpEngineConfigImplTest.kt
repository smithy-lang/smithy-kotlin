/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.http.config.HttpEngineConfig
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngineConfig
import aws.smithy.kotlin.runtime.io.closeIfCloseable
import org.junit.jupiter.api.Test
import kotlin.test.*

class HttpEngineConfigImplTest {
    @Test
    fun testSpecificInstance() {
        val instance = CrtHttpEngine { initialWindowSizeBytes = 1024 }
        config { httpClient = instance }.use { config ->
            assertEquals(instance, config.httpClient)
        }
    }

    @Test
    fun testBuildInstanceWithConfig() {
        config { httpClient { maxConnections = 256u } }.use { config ->
            assertEquals(256u, config.httpClient.config.maxConnections)
        }
    }

    @Test
    fun testBuildInstanceOfType() {
        config { httpClient(CrtHttpEngine) }.use { config ->
            assertIs<CrtHttpEngineConfig>(config.httpClient.config)
        }
    }

    @Test
    fun testBuildInstanceOfTypeWithConfig() {
        config {
            httpClient(CrtHttpEngine) {
                initialWindowSizeBytes = 1024
            }
        }.use { config ->
            assertIs<CrtHttpEngineConfig>(config.httpClient.config)
            assertEquals(1024, (config.httpClient.config as CrtHttpEngineConfig).initialWindowSizeBytes)
        }
    }

    @Test
    fun testConfigThenInstance() {
        val instance = CrtHttpEngine { initialWindowSizeBytes = 1024 }
        config {
            httpClient { maxConnections = 256u }
            httpClient = instance
        }.use { config ->
            assertEquals(instance, config.httpClient)
        }
    }

    @Test
    fun testInitialInstanceThenConfig() {
        CrtHttpEngine { initialWindowSizeBytes = 1024 }.use { instance ->
            config {
                httpClient = instance
                httpClient { maxConnections = 256u }
            }.use { config ->
                assertNotEquals(instance, config.httpClient)
                assertEquals(256u, config.httpClient.config.maxConnections)
            }
        }
    }

    @Test
    fun testInitialInstanceThenConfigThenAnotherInstance() {
        CrtHttpEngine { initialWindowSizeBytes = 1024 }.use { firstInstance ->
            val secondInstance = CrtHttpEngine { initialWindowSizeBytes = 2048 }
            config {
                httpClient = firstInstance
                httpClient { maxConnections = 256u }
                httpClient = secondInstance
            }.use { config ->
                assertNotEquals(firstInstance, config.httpClient)
                assertEquals(secondInstance, config.httpClient)
            }
        }
    }

    @Test
    fun testConfigIsAdditive() {
        config {
            httpClient {
                maxConnections = 256u
            }
            httpClient(CrtHttpEngine) {
                initialWindowSizeBytes = 1024
            }
        }.use { config ->
            assertIs<CrtHttpEngineConfig>(config.httpClient.config)
            assertEquals(256u, config.httpClient.config.maxConnections)
            assertEquals(1024, (config.httpClient.config as CrtHttpEngineConfig).initialWindowSizeBytes)
        }
    }

    @Test
    fun testGenericConfigDoesntClobberSpecificConfig() {
        config {
            httpClient(CrtHttpEngine) {
                initialWindowSizeBytes = 1024
            }
            httpClient {
                maxConnections = 256u
            }
        }.use { config ->
            assertIs<CrtHttpEngineConfig>(config.httpClient.config)
            assertEquals(256u, config.httpClient.config.maxConnections)
            assertEquals(1024, (config.httpClient.config as CrtHttpEngineConfig).initialWindowSizeBytes)
        }
    }

    @Test
    fun testConfigAfterEngineFails() {
        CrtHttpEngine { initialWindowSizeBytes = 1024 }.use { instance ->
            assertFailsWith<ClientException> {
                config {
                    httpClient { maxConnections = 256u }
                    httpClient = instance
                    httpClient { maxConnections = 256u }
                }
            }
        }
    }
}

private fun config(block: HttpEngineConfig.Builder.() -> Unit): HttpEngineConfig =
    HttpEngineConfigImpl.BuilderImpl().apply(block).buildHttpEngineConfig()

private fun HttpEngineConfig.use(block: (HttpEngineConfig) -> Unit) {
    try {
        block(this)
    } finally {
        httpClient.closeIfCloseable()
    }
}
