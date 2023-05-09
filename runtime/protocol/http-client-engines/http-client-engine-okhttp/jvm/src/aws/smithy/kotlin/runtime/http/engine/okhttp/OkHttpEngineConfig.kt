/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.config.EngineFactory
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfigImpl

/**
 * The configuration parameters for an OkHttp HTTP client engine.
 */
public interface OkHttpEngineConfig : HttpClientEngineConfig {
    public companion object {
        /**
         * Initializes a new [OkHttpEngineConfig] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: Builder.() -> Unit): OkHttpEngineConfig =
            OkHttpEngineConfigImpl(Builder().apply(block))

        /**
         * The default engine config. Most clients should use this.
         */
        public val Default: OkHttpEngineConfig = OkHttpEngineConfigImpl(Builder())
    }

    /**
     * The maximum number of connections to open to a single host.
     */
    public val maxConnectionsPerHost: UInt

    /**
     * A builder for [OkHttpEngineConfig]
     */
    public interface Builder : HttpClientEngineConfig.Builder {
        public companion object {
            /**
             * Creates a new, empty builder for an [OkHttpEngineConfig]
             */
            public operator fun invoke(): Builder = OkHttpEngineConfigImpl.BuilderImpl()
        }

        /**
         * The maximum number of connections to open to a single host. Defaults to [maxConnections].
         */
        public var maxConnectionsPerHost: UInt?
    }
}

internal class OkHttpEngineConfigImpl(builder: OkHttpEngineConfig.Builder) : OkHttpEngineConfig, HttpClientEngineConfigImpl(builder) {
    override val maxConnectionsPerHost: UInt = builder.maxConnectionsPerHost ?: builder.maxConnections

    override fun toBuilderApplicator(): HttpClientEngineConfig.Builder.() -> Unit = {
        super.toBuilderApplicator()()

        if (this is OkHttpEngineConfig.Builder) {
            maxConnectionsPerHost = this@OkHttpEngineConfigImpl.maxConnectionsPerHost
        }
    }

    internal class BuilderImpl : OkHttpEngineConfig.Builder, HttpClientEngineConfigImpl.BuilderImpl() {
        override var maxConnectionsPerHost: UInt? = null
    }
}

/**
 * Specifies the OkHttp engine
 */
public object OkHttp : EngineFactory<OkHttpEngineConfig.Builder, OkHttpEngine> {
    override val engineConstructor: (OkHttpEngineConfig.Builder.() -> Unit) -> OkHttpEngine =
        OkHttpEngine.Companion::invoke
}
