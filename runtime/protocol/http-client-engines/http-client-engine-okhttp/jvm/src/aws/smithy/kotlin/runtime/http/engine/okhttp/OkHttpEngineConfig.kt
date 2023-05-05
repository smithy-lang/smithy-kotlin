/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.config.HttpEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfigImpl

public interface OkHttpEngineConfig : HttpClientEngineConfig {
    public companion object {
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

    public interface Builder : HttpClientEngineConfig.Builder {
        public companion object {
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

public fun HttpEngineConfig.Builder.okHttpEngine(block: OkHttpEngineConfig.Builder.() -> Unit) {
    engineConstructor = OkHttpEngine.Companion::invoke
    httpClientEngine {
        this as OkHttpEngineConfig.Builder
        block()
    }
}
