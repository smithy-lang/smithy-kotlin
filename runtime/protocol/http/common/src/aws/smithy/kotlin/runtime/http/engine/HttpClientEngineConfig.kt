/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine

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
public open class HttpClientEngineConfig constructor(builder: Builder) {
    public constructor() : this(Builder())

    public companion object {
        public operator fun invoke(block: Builder.() -> Unit): HttpClientEngineConfig =
            HttpClientEngineConfig(Builder().apply(block))

        /**
         * Default client engine config
         */
        public val Default: HttpClientEngineConfig = HttpClientEngineConfig(Builder())
    }

    /**
     * Timeout for each read to an underlying socket
     */
    public val socketReadTimeout: Duration = builder.socketReadTimeout

    /**
     * Timeout for each write to an underlying socket
     */
    public val socketWriteTimeout: Duration = builder.socketWriteTimeout

    /**
     * Maximum number of open connections
     */
    public val maxConnections: UInt = builder.maxConnections

    /**
     * The amount of time to wait for a connection to be established
     */
    public val connectTimeout: Duration = builder.connectTimeout

    /**
     * The amount of time to wait for an already-established connection from a connection pool
     */
    public val connectionAcquireTimeout: Duration = builder.connectionAcquireTimeout

    /**
     * The amount of time before an idle connection should be reaped from a connection pool. Zero indicates that
     * idle connections should never be reaped.
     */
    public val connectionIdleTimeout: Duration = builder.connectionIdleTimeout

    /**
     * The ALPN protocol list when a TLS connection starts
     */
    public val alpn: List<AlpnId> = builder.alpn

    /**
     * The proxy selection policy
     */
    public val proxySelector: ProxySelector = builder.proxySelector

    public open class Builder {
        /**
         * Timeout for each read to an underlying socket
         */
        public var socketReadTimeout: Duration = 30.seconds

        /**
         * Timeout for each write to an underlying socket
         */
        public var socketWriteTimeout: Duration = 30.seconds

        /**
         * Maximum number of open connections
         */
        public var maxConnections: UInt = 16u

        /**
         * The amount of time to wait for a connection to be established
         */
        public var connectTimeout: Duration = 2.seconds

        /**
         * The amount of time to wait for an already-established connection from a connection pool
         */
        public var connectionAcquireTimeout: Duration = 10.seconds

        /**
         * The amount of time before an idle connection should be reaped from a connection pool. Zero indicates that
         * idle connections should never be reaped.
         */
        public var connectionIdleTimeout: Duration = 60.seconds

        /**
         * Set the ALPN protocol list when a TLS connection starts
         */
        public var alpn: List<AlpnId> = emptyList()

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
        public var proxySelector: ProxySelector = EnvironmentProxySelector()
    }
}

/**
 * Common ALPN identifiers
 * See the [IANA registry](https://www.iana.org/assignments/tls-extensiontype-values/tls-extensiontype-values.xhtml#alpn-protocol-ids)
 */
public enum class AlpnId(public val protocolId: String) {
    /**
     * HTTP 1.1
     */
    HTTP1_1("http/1.1"),

    /**
     * HTTP 2 over TLS
     */
    HTTP2("h2"),

    /**
     * HTTP 3
     */
    HTTP3("h3")
}
