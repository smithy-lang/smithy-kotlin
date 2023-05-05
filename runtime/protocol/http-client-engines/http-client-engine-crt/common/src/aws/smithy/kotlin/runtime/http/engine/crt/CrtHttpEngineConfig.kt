/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.io.ClientBootstrap
import aws.smithy.kotlin.runtime.http.config.HttpEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfigImpl

/**
 * Describes configuration options for the CRT HTTP engine. Use [Default] for the standard configuration or use
 * [Builder] to build a custom configuration.
 */
public interface CrtHttpEngineConfig : HttpClientEngineConfig {
    public companion object {
        public operator fun invoke(block: Builder.() -> Unit): CrtHttpEngineConfig =
            CrtHttpEngineConfigImpl(Builder().apply(block))

        /**
         * The default engine config. Most clients should use this.
         */
        public val Default: CrtHttpEngineConfig = CrtHttpEngineConfigImpl(Builder())
    }

    /**
     * The amount of data that can be buffered before reading from the socket will cease. Reading will
     * resume as data is consumed.
     */
    public val initialWindowSizeBytes: Int

    /**
     * The [ClientBootstrap] to use for the engine. By default it is a shared instance.
     */
    public var clientBootstrap: ClientBootstrap?

    public interface Builder : HttpClientEngineConfig.Builder {
        public companion object {
            public operator fun invoke(): Builder = CrtHttpEngineConfigImpl.BuilderImpl()
        }

        /**
         * Set the amount of data that can be buffered before reading from the socket will cease. Reading will
         * resume as data is consumed.
         */
        public var initialWindowSizeBytes: Int

        /**
         * Set the [ClientBootstrap] to use for the engine. By default it is a shared instance.
         */
        public var clientBootstrap: ClientBootstrap?
    }
}

internal class CrtHttpEngineConfigImpl constructor(builder: CrtHttpEngineConfig.Builder) : CrtHttpEngineConfig, HttpClientEngineConfigImpl(builder) {
    override val initialWindowSizeBytes: Int = builder.initialWindowSizeBytes
    override var clientBootstrap: ClientBootstrap? = builder.clientBootstrap

    override fun toBuilderApplicator(): HttpClientEngineConfig.Builder.() -> Unit = {
        super.toBuilderApplicator()()

        if (this is CrtHttpEngineConfig.Builder) {
            initialWindowSizeBytes = this@CrtHttpEngineConfigImpl.initialWindowSizeBytes
            clientBootstrap = this@CrtHttpEngineConfigImpl.clientBootstrap
        }
    }

    internal class BuilderImpl : CrtHttpEngineConfig.Builder, HttpClientEngineConfigImpl.BuilderImpl() {
        override var initialWindowSizeBytes: Int = DEFAULT_WINDOW_SIZE_BYTES
        override var clientBootstrap: ClientBootstrap? = null
    }
}

public fun HttpEngineConfig.Builder.crtHttpEngine(block: CrtHttpEngineConfig.Builder.() -> Unit) {
    engineConstructor = CrtHttpEngine.Companion::invoke
    httpClientEngine {
        this as CrtHttpEngineConfig.Builder
        block()
    }
}
