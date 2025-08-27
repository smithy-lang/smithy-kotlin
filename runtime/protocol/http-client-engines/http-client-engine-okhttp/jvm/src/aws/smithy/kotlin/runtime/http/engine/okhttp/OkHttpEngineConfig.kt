/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfigImpl
import aws.smithy.kotlin.runtime.telemetry.Global
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import okhttp3.CertificatePinner
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.X509TrustManager
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

    /**
     * Trust manager used to validate server certificates during TLS handshake.
     * Determines whether to trust the certificate chain presented by a remote server.
     * When provided, this trust manager will be used instead of the default system trust store.
     */
    public var trustManager: X509TrustManager? = builder.trustManager

    /**
     * Key manager that supplies client certificates for mutual TLS (mTLS) authentication.
     * When provided, the client will present certificates from this key manager when the server
     * requests client authentication. Used for scenarios requiring client certificate authentication.
     */
    public var keyManager: KeyManager? = builder.keyManager

    /**
     * List of cipher suites to enable for TLS connections. If null, uses OkHttp defaults.
     * When specified, only the listed cipher suites will be enabled.
     */
    public val cipherSuites: List<String>? = builder.cipherSuites

    /**
     * Certificate pinner that validates server certificates against known public key pins.
     * Used to prevent man-in-the-middle attacks by ensuring the server presents expected certificates.
     */
    public val certificatePinner: CertificatePinner? = builder.certificatePinner

    /**
     * Custom hostname verifier for validating server hostnames during TLS handshake.
     * By default, OkHttp verifies that the certificate's hostname matches the request hostname.
     * Use this to implement custom hostname verification logic.
     */
    public val hostnameVerifier: HostnameVerifier? = builder.hostnameVerifier

    override fun toBuilderApplicator(): HttpClientEngineConfig.Builder.() -> Unit = {
        super.toBuilderApplicator()()

        if (this is Builder) {
            connectionIdlePollingInterval = this@OkHttpEngineConfig.connectionIdlePollingInterval
            maxConcurrencyPerHost = this@OkHttpEngineConfig.maxConcurrencyPerHost
            trustManager = this@OkHttpEngineConfig.trustManager
            keyManager = this@OkHttpEngineConfig.keyManager
            cipherSuites = this@OkHttpEngineConfig.cipherSuites
            certificatePinner = this@OkHttpEngineConfig.certificatePinner
            hostnameVerifier = this@OkHttpEngineConfig.hostnameVerifier
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

        /**
         * Trust manager used to validate server certificates during TLS handshake.
         * Determines whether to trust the certificate chain presented by a remote server.
         * When provided, this trust manager will be used instead of the default system trust store.
         */
        public var trustManager: X509TrustManager? = null

        /**
         * Key manager that supplies client certificates for mutual TLS (mTLS) authentication.
         * When provided, the client will present certificates from this key manager when the server
         * requests client authentication. Used for scenarios requiring client certificate authentication.
         */
        public var keyManager: KeyManager? = null

        /**
         * List of cipher suites to enable for TLS connections. If null, uses OkHttp defaults.
         * When specified, only the listed cipher suites will be enabled.
         */
        public var cipherSuites: List<String>? = null

        /**
         * Certificate pinner that validates server certificates against known public key pins.
         * Used to prevent man-in-the-middle attacks by ensuring the server presents expected certificates.
         */
        public var certificatePinner: CertificatePinner? = null

        /**
         * Custom hostname verifier for validating server hostnames during TLS handshake.
         * By default, OkHttp verifies that the certificate's hostname matches the request hostname.
         * Use this to implement custom hostname verification logic.
         */
        public var hostnameVerifier: HostnameVerifier? = null

        override var telemetryProvider: TelemetryProvider = TelemetryProvider.Global
    }
}
