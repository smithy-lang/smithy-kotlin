/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.client.config.ClientSettings
import aws.smithy.kotlin.runtime.config.resolve
import aws.smithy.kotlin.runtime.http.config.HttpEngineConfigDsl
import aws.smithy.kotlin.runtime.net.TlsVersion

/**
 * Defines values related to TLS and secure connections.
 */
public class TlsContext internal constructor(builder: Builder) {
    public companion object {
        public operator fun invoke(block: Builder.() -> Unit): TlsContext = TlsContext(Builder().apply(block))

        public val Default: TlsContext = TlsContext(Builder())
    }

    /**
     * The ALPN protocol list when a TLS connection starts
     */
    public val alpn: List<AlpnId> = builder.alpn

    /**
     * The minimum allowed TLS version for HTTP connections
     */
    public val minVersion: TlsVersion? = builder.minVersion ?: ClientSettings.MinTlsVersion.resolve()

    internal fun toBuilder(): Builder = Builder().apply {
        alpn = this@TlsContext.alpn
        minVersion = this@TlsContext.minVersion
    }

    /**
     * Mutable configuration for [TlsContext]
     */
    @HttpEngineConfigDsl
    public class Builder {
        /**
         * The ALPN protocol list when a TLS connection starts
         */
        public var alpn: List<AlpnId> = emptyList()

        /**
         * The minimum allowed TLS version for HTTP connections
         */
        public var minVersion: TlsVersion? = null
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
     * Cleartext HTTP/2 with no "upgrade" round trip. This option requires the client to have prior knowledge that the
     * server supports cleartext HTTP/2. See also rfc_7540_34.
     */
    H2_PRIOR_KNOWLEDGE("h2_prior_knowledge"),

    /**
     * HTTP 3
     */
    HTTP3("h3"),
}
