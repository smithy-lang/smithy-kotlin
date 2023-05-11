/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.io.ClientBootstrap
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfigImpl

/**
 * Describes configuration options for the CRT HTTP engine. Use [Default] for the standard configuration or use
 * [Builder] to build a custom configuration.
 */
public class CrtHttpEngineConfig private constructor(builder: Builder) : HttpClientEngineConfigImpl(builder) {
    public companion object {
        /**
         * Initializes a new [CrtHttpEngineConfig] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: Builder.() -> Unit): CrtHttpEngineConfig =
            CrtHttpEngineConfig(Builder().apply(block))

        /**
         * The default engine config. Most clients should use this.
         */
        public val Default: CrtHttpEngineConfig = CrtHttpEngineConfig(Builder())
    }

    /**
     * The amount of data that can be buffered before reading from the socket will cease. Reading will
     * resume as data is consumed.
     */
    public val initialWindowSizeBytes: Int = builder.initialWindowSizeBytes

    /**
     * The [ClientBootstrap] to use for the engine. By default it is a shared instance.
     */
    public var clientBootstrap: ClientBootstrap? = builder.clientBootstrap

    override fun toBuilderApplicator(): HttpClientEngineConfig.Builder.() -> Unit = {
        super.toBuilderApplicator()()

        if (this is Builder) {
            initialWindowSizeBytes = this@CrtHttpEngineConfig.initialWindowSizeBytes
            clientBootstrap = this@CrtHttpEngineConfig.clientBootstrap
        }
    }

    /**
     * A builder for [CrtHttpEngineConfig]
     */
    public class Builder : BuilderImpl() {
        /**
         * Set the amount of data that can be buffered before reading from the socket will cease. Reading will
         * resume as data is consumed.
         */
        public var initialWindowSizeBytes: Int = DEFAULT_WINDOW_SIZE_BYTES

        /**
         * Set the [ClientBootstrap] to use for the engine. By default it is a shared instance.
         */
        public var clientBootstrap: ClientBootstrap? = null
    }
}
