/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.io.ClientBootstrap
import aws.sdk.kotlin.crt.io.TlsCipherPreference
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
     * Maximum number of open connections
     */
    public val maxConnections: UInt = builder.maxConnections

    /**
     * The amount of data that can be buffered before reading from the socket will cease. Reading will
     * resume as data is consumed.
     */
    public val initialWindowSizeBytes: Int = builder.initialWindowSizeBytes

    /**
     * The [ClientBootstrap] to use for the engine. By default it is a shared instance.
     */
    public var clientBootstrap: ClientBootstrap? = builder.clientBootstrap

    /**
     * Certificate Authority content in PEM format
     * Mutually exclusive with caFile and caDir.
     */
    public var caRoot: String? = builder.caRoot

    /**
     * Path to the root certificate. Must be in PEM format.
     * Mutually exclusive with caRoot.
     */
    public var caFile: String? = builder.caFile

    /**
     * Path to the local trust store. Can be null.
     * Mutually exclusive with caRoot.
     */
    public var caDir: String? = builder.caDir

    /**
     * TLS cipher suite preference for connections.
     * Controls which cipher suites are available during TLS negotiation.
     */
    public var cipherPreference: TlsCipherPreference = builder.cipherPreference

    /**
     * Whether to verify the peer's certificate during TLS handshake.
     * When false, accepts any certificate (insecure, for testing only).
     */
    public var verifyPeer: Boolean = builder.verifyPeer

    override fun toBuilderApplicator(): HttpClientEngineConfig.Builder.() -> Unit = {
        super.toBuilderApplicator()()

        if (this is Builder) {
            maxConnections = this@CrtHttpEngineConfig.maxConnections
            initialWindowSizeBytes = this@CrtHttpEngineConfig.initialWindowSizeBytes
            clientBootstrap = this@CrtHttpEngineConfig.clientBootstrap
            caRoot = this@CrtHttpEngineConfig.caRoot
            caFile = this@CrtHttpEngineConfig.caFile
            caDir = this@CrtHttpEngineConfig.caDir
            cipherPreference = this@CrtHttpEngineConfig.cipherPreference
            verifyPeer = this@CrtHttpEngineConfig.verifyPeer
        }
    }

    /**
     * A builder for [CrtHttpEngineConfig]
     */
    public class Builder : BuilderImpl() {
        /**
         * Maximum number of open connections
         */
        public var maxConnections: UInt = 64u

        /**
         * Set the amount of data that can be buffered before reading from the socket will cease. Reading will
         * resume as data is consumed.
         */
        public var initialWindowSizeBytes: Int = DEFAULT_WINDOW_SIZE_BYTES

        /**
         * Set the [ClientBootstrap] to use for the engine. By default it is a shared instance.
         */
        public var clientBootstrap: ClientBootstrap? = null

        /**
         * Certificate Authority content in PEM format.
         * Mutually exclusive with caFile and caDir.
         */
        public var caRoot: String? = null

        /**
         * Path to the root certificate. Must be in PEM format.
         * Mutually exclusive with caRoot.
         */
        public var caFile: String? = null

        /**
         * Path to the local trust store. Can be null.
         * Mutually exclusive with caRoot.
         */
        public var caDir: String? = null

        /**
         * TLS cipher suite preference for connections.
         * Controls which cipher suites are available during TLS negotiation.
         */
        public var cipherPreference: TlsCipherPreference = TlsCipherPreference.SYSTEM_DEFAULT

        /**
         * Whether to verify the peer's certificate during TLS handshake.
         * When false, accepts any certificate (insecure, for testing only).
         */
        public var verifyPeer: Boolean = true
    }
}
