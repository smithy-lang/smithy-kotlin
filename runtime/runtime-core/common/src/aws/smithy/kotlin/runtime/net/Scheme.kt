/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

/**
 * Represents a wire protocol
 * @property protocolName name of protocol
 * @property defaultPort default port for the protocol
 */
public data class Scheme(val protocolName: String, val defaultPort: Int) {
    public companion object {

        /**
         * HTTPS over port 443
         */
        public val HTTPS: Scheme = Scheme("https", 443)

        /**
         * HTTP over port 80
         */
        public val HTTP: Scheme = Scheme("http", 80)

        /**
         * WebSocket over port 80
         */
        public val WS: Scheme = Scheme("ws", 80)

        /**
         * Secure WebSocket over port 443
         */
        public val WSS: Scheme = Scheme("wss", 443)

        /**
         * Protocols by names map
         */
        public val byName: Map<String, Scheme> = listOf(HTTP, HTTPS, WS, WSS).associateBy { it.protocolName }

        /**
         * Parse a protocol scheme string into a [Scheme] instance
         */
        public fun parse(scheme: String): Scheme = byName[scheme.lowercase()] ?: Scheme(scheme, -1)
    }
}
