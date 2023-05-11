/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfigImpl

/**
 * The configuration parameters for an OkHttp HTTP client engine.
 */
public class OkHttpEngineConfig private constructor(builder: Builder) : HttpClientEngineConfigImpl(builder) {
    public companion object {
        /**
         * Initializes a new [OkHttpEngineConfig] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: Builder.() -> Unit): OkHttpEngineConfig =
            OkHttpEngineConfig(Builder().apply(block))

        /**
         * The default engine config. Most clients should use this.
         */
        public val Default: OkHttpEngineConfig = OkHttpEngineConfig(Builder())
    }

    /**
     * The maximum number of connections to open to a single host.
     */
    public val maxConnectionsPerHost: UInt = builder.maxConnectionsPerHost ?: builder.maxConnections

    override fun toBuilderApplicator(): HttpClientEngineConfig.Builder.() -> Unit = {
        super.toBuilderApplicator()()

        if (this is Builder) {
            maxConnectionsPerHost = this@OkHttpEngineConfig.maxConnectionsPerHost
        }
    }

    /**
     * A builder for [OkHttpEngineConfig]
     */
    public class Builder : BuilderImpl() {
        /**
         * The maximum number of connections to open to a single host. Defaults to [maxConnections].
         */
        public var maxConnectionsPerHost: UInt? = null
    }
}
