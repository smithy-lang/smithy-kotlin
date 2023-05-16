/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.config.EngineFactory
import aws.smithy.kotlin.runtime.http.config.HttpEngineConfig
import aws.smithy.kotlin.runtime.http.engine.internal.manage

private typealias ConfigApplicator = HttpClientEngineConfig.Builder.() -> Unit

@InternalApi
public class HttpEngineConfigImpl private constructor(override val httpClient: HttpClientEngine) : HttpEngineConfig {
    @InternalApi
    public class BuilderImpl : HttpEngineConfig.Builder {
        private var configApplicator: ConfigApplicator = {}
        private var engineConstructor: (ConfigApplicator) -> HttpClientEngine = ::DefaultHttpEngine
        private var engineSupplier: () -> HttpClientEngine = { engineConstructor {}.manage() }
        private var state = SupplierState.NOT_INITIALIZED

        override var httpClient: HttpClientEngine? = null
            set(value) {
                state = when (state) {
                    SupplierState.NOT_INITIALIZED -> SupplierState.INITIALIZED
                    else -> SupplierState.EXPLICIT_ENGINE
                }

                field = value
                engineSupplier = when (value) {
                    null -> {
                        // Reset engine type back to default
                        engineConstructor = ::DefaultHttpEngine
                        { engineConstructor {}.manage() }
                    }
                    else -> { { value } }
                }
                configApplicator = value?.config?.toBuilderApplicator() ?: {}
            }

        override fun httpClient(block: HttpClientEngineConfig.Builder.() -> Unit) {
            httpClientImpl<HttpClientEngineConfig.Builder, HttpClientEngine>(null, block)
        }

        override fun <B : HttpClientEngineConfig.Builder, E : HttpClientEngine> httpClient(
            engineFactory: EngineFactory<B, E>,
            block: B.() -> Unit,
        ) {
            httpClientImpl(engineFactory, block)
        }

        private fun <B : HttpClientEngineConfig.Builder, E : HttpClientEngine> httpClientImpl(
            engineFactory: EngineFactory<B, E>?,
            block: B.() -> Unit,
        ) {
            when (state) {
                SupplierState.EXPLICIT_ENGINE -> throw ClientException("Engine configuration cannot be given after an explicit engine instance has already been set")
                else -> state = SupplierState.EXPLICIT_CONFIG
            }

            engineFactory?.let { engineConstructor = it.engineConstructor }

            val previousApplicator = configApplicator
            configApplicator = {
                previousApplicator()

                @Suppress("UNCHECKED_CAST") // This is safe because [engineConstructor] is definitely the right type
                block(this as B)
            }
            engineSupplier = { engineConstructor(configApplicator).manage() }
        }

        override fun buildHttpEngineConfig(): HttpEngineConfig = HttpEngineConfigImpl(engineSupplier())
    }

    private enum class SupplierState {
        NOT_INITIALIZED,
        INITIALIZED,
        EXPLICIT_CONFIG,
        EXPLICIT_ENGINE,
    }
}
