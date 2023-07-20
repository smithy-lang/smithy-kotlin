/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.config.HttpEngineConfigDsl
import aws.smithy.kotlin.runtime.net.HostResolver
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// See https://github.com/aws/aws-sdk-java-v2/blob/master/http-client-spi/src/main/java/software/amazon/awssdk/http/SdkHttpConfigurationOption.java
// for all the options the Java v2 SDK supports
/**
 * Common configuration options to be interpreted by an underlying engine
 *
 * NOTE: Not all engines will support every option! Engines *SHOULD* log a warning when given a configuration
 * option they don't understand/support
 */
public interface HttpClientEngineConfig {
    public companion object {
        /**
         * Initializes a new [HttpClientEngineConfig] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: Builder.() -> Unit): HttpClientEngineConfig =
            HttpClientEngineConfigImpl(Builder().apply(block))

        /**
         * Default client engine config
         */
        public val Default: HttpClientEngineConfig = HttpClientEngineConfigImpl(Builder())
    }

    /**
     * Timeout for each read to an underlying socket
     */
    public val socketReadTimeout: Duration

    /**
     * Timeout for each write to an underlying socket
     */
    public val socketWriteTimeout: Duration

    /**
     * Maximum number of open connections
     */
    public val maxConnections: UInt

    /**
     * The amount of time to wait for a connection to be established
     */
    public val connectTimeout: Duration

    /**
     * The amount of time to wait for an already-established connection from a connection pool
     */
    public val connectionAcquireTimeout: Duration

    /**
     * The amount of time before an idle connection should be reaped from a connection pool. Zero indicates that
     * idle connections should never be reaped.
     */
    public val connectionIdleTimeout: Duration

    /**
     * The maximum number of requests that will be executed concurrently by an engine. Beyond this requests
     * will be queued waiting to be executed by the engine.
     */
    public val maxConcurrency: UInt

    /**
     * The proxy selection policy
     */
    public val proxySelector: ProxySelector

    /**
     * The host name resolver (DNS)
     */
    @InternalApi
    public val hostResolver: HostResolver

    /**
     * Settings related to TLS and secure connections
     */
    public val tlsContext: TlsContext

    /**
     * The telemetry provider that the HTTP client will be instrumented with
     */
    public val telemetryProvider: TelemetryProvider

    @InternalApi
    public fun toBuilderApplicator(): Builder.() -> Unit

    /**
     * A builder for [HttpClientEngineConfig]
     */
    @HttpEngineConfigDsl
    public interface Builder {
        public companion object {
            /**
             * Creates a new, empty builder for an [HttpClientEngineConfig]
             */
            public operator fun invoke(): Builder = HttpClientEngineConfigImpl.BuilderImpl()
        }

        /**
         * Timeout for each read to an underlying socket
         */
        public var socketReadTimeout: Duration

        /**
         * Timeout for each write to an underlying socket
         */
        public var socketWriteTimeout: Duration

        /**
         * Maximum number of open connections
         */
        public var maxConnections: UInt

        /**
         * The amount of time to wait for a connection to be established
         */
        public var connectTimeout: Duration

        /**
         * The amount of time to wait for an already-established connection from a connection pool
         */
        public var connectionAcquireTimeout: Duration

        /**
         * The amount of time before an idle connection should be reaped from a connection pool. Zero indicates that
         * idle connections should never be reaped.
         */
        public var connectionIdleTimeout: Duration

        /**
         * The maximum number of requests that will be executed concurrently by an engine. Beyond this requests
         * will be queued waiting to be executed by the engine.
         */
        public var maxConcurrency: UInt

        /**
         * Set the proxy selection policy to be used.
         *
         * The default behavior is to respect common proxy system properties and environment variables.
         *
         * **JVM System Properties**:
         * - `http.proxyHost`
         * - `http.proxyPort`
         * - `https.proxyHost`
         * - `https.proxyPort`
         * - `http.noProxyHosts`
         *
         * **Environment variables in the given order**:
         * - `http_proxy`, `HTTP_PROXY`
         * - `https_proxy`, `HTTPS_PROXY`
         * - `no_proxy`, `NO_PROXY`
         *
         * # Disabling proxy selection explicitly by using [ProxySelector.NoProxy]
         *
         * ```
         * proxySelector = ProxySelector.NoProxy
         * ```
         */
        public var proxySelector: ProxySelector

        /**
         * The host name resolver (DNS) to be used by the client
         */
        @InternalApi
        public var hostResolver: HostResolver

        /**
         * Settings related to TLS and secure connections
         */
        public var tlsContext: TlsContext

        /**
         * The telemetry provider that the HTTP client will be instrumented with
         */
        public var telemetryProvider: TelemetryProvider

        /**
         * Settings related to TLS and secure connections
         */
        public fun tlsContext(block: TlsContext.Builder.() -> Unit)
    }
}

@InternalApi
public open class HttpClientEngineConfigImpl(builder: HttpClientEngineConfig.Builder) : HttpClientEngineConfig {
    @InternalApi
    public constructor() : this(BuilderImpl())

    override val socketReadTimeout: Duration = builder.socketReadTimeout
    override val socketWriteTimeout: Duration = builder.socketWriteTimeout
    override val maxConnections: UInt = builder.maxConnections
    override val connectTimeout: Duration = builder.connectTimeout
    override val connectionAcquireTimeout: Duration = builder.connectionAcquireTimeout
    override val connectionIdleTimeout: Duration = builder.connectionIdleTimeout
    override val maxConcurrency: UInt = builder.maxConcurrency
    override val proxySelector: ProxySelector = builder.proxySelector
    override val hostResolver: HostResolver = builder.hostResolver
    override val tlsContext: TlsContext = builder.tlsContext
    override val telemetryProvider: TelemetryProvider = builder.telemetryProvider

    override fun toBuilderApplicator(): HttpClientEngineConfig.Builder.() -> Unit = {
        socketReadTimeout = this@HttpClientEngineConfigImpl.socketReadTimeout
        socketWriteTimeout = this@HttpClientEngineConfigImpl.socketWriteTimeout
        maxConnections = this@HttpClientEngineConfigImpl.maxConnections
        connectTimeout = this@HttpClientEngineConfigImpl.connectTimeout
        connectionAcquireTimeout = this@HttpClientEngineConfigImpl.connectionAcquireTimeout
        connectionIdleTimeout = this@HttpClientEngineConfigImpl.connectionIdleTimeout
        maxConcurrency = this@HttpClientEngineConfigImpl.maxConcurrency
        proxySelector = this@HttpClientEngineConfigImpl.proxySelector
        hostResolver = this@HttpClientEngineConfigImpl.hostResolver
        tlsContext = this@HttpClientEngineConfigImpl.tlsContext
        telemetryProvider = this@HttpClientEngineConfigImpl.telemetryProvider
    }

    @InternalApi
    public open class BuilderImpl : HttpClientEngineConfig.Builder {
        override var socketReadTimeout: Duration = 30.seconds
        override var socketWriteTimeout: Duration = 30.seconds
        override var maxConnections: UInt = 64u
        override var connectTimeout: Duration = 2.seconds
        override var connectionAcquireTimeout: Duration = 10.seconds
        override var connectionIdleTimeout: Duration = 60.seconds
        override var maxConcurrency: UInt = 128u
        override var proxySelector: ProxySelector = EnvironmentProxySelector()
        override var hostResolver: HostResolver = HostResolver.Default
        override var tlsContext: TlsContext = TlsContext.Default
        override var telemetryProvider: TelemetryProvider = TelemetryProvider.None
        override fun tlsContext(block: TlsContext.Builder.() -> Unit) {
            tlsContext = TlsContext(tlsContext.toBuilder().apply(block))
        }
    }
}
