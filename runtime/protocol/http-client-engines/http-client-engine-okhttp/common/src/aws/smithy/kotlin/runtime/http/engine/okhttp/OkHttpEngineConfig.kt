/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfigImpl
import aws.smithy.kotlin.runtime.telemetry.Global
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import kotlin.time.Duration

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
     * The interval in which to poll idle connections for remote closure or `null` to disable monitoring of idle
     * connections. The default value is `null`.
     *
     * When this value is non-`null`, polling is enabled on connections which are released from an engine call and
     * enter the connection pool. Polling consists of a loop that performs blocking reads with the socket timeout
     * set to [connectionIdlePollingInterval]. Polling is cancelled for a connection when the engine acquires it
     * from the pool or when the connection is evicted from the pool and closed. Because the polling loop uses
     * blocking reads, an engine call to acquire or close a connection may be delayed by as much as
     * [connectionIdlePollingInterval].
     *
     * When this value is `null`, polling is disabled. Idle connections in the pool which are closed remotely may
     * encounter errors when they are acquired for a subsequent call.
     */
    public val connectionIdlePollingInterval: Duration? = builder.connectionIdlePollingInterval

    /**
     * The maximum number of requests to execute concurrently for a single host.
     */
    public val maxConcurrencyPerHost: UInt = builder.maxConcurrencyPerHost ?: builder.maxConcurrency

    override fun toBuilderApplicator(): HttpClientEngineConfig.Builder.() -> Unit = {
        super.toBuilderApplicator()()

        if (this is Builder) {
            connectionIdlePollingInterval = this@OkHttpEngineConfig.connectionIdlePollingInterval
            maxConcurrencyPerHost = this@OkHttpEngineConfig.maxConcurrencyPerHost
        }
    }

    /**
     * A builder for [OkHttpEngineConfig]
     */
    public class Builder : BuilderImpl() {
        /**
         * The interval in which to poll idle connections for remote closure or `null` to disable monitoring of idle
         * connections. The default value is `null`.
         *
         * When this value is non-`null`, polling is enabled on connections which are released from an engine call and
         * enter the connection pool. Polling consists of a loop that performs blocking reads with the socket timeout
         * set to [connectionIdlePollingInterval]. Polling is cancelled for a connection when the engine acquires it
         * from the pool or when the connection is evicted from the pool and closed. Because the polling loop uses
         * blocking reads, an engine call to acquire or close a connection may be delayed by as much as
         * [connectionIdlePollingInterval].
         *
         * When this value is `null`, polling is disabled. Idle connections in the pool which are closed remotely may
         * encounter errors when they are acquired for a subsequent call.
         */
        public var connectionIdlePollingInterval: Duration? = null

        /**
         * The maximum number of requests to execute concurrently for a single host. Defaults to [maxConcurrency].
         */
        public var maxConcurrencyPerHost: UInt? = null

        override var telemetryProvider: TelemetryProvider = TelemetryProvider.Global
    }
}
