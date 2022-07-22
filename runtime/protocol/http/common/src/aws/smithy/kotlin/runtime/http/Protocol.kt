/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http

/**
 * Represents a wire protocol
 * @property protocolName name of protocol
 * @property defaultPort default port for the protocol
 */
public data class Protocol(val protocolName: String, val defaultPort: Int) {
    public companion object {

        /**
         * HTTPS over port 443
         */
        public val HTTPS: Protocol = Protocol("https", 443)

        /**
         * HTTP over port 80
         */
        public val HTTP: Protocol = Protocol("http", 80)

        /**
         * WebSocket over port 80
         */
        public val WS: Protocol = Protocol("ws", 80)

        /**
         * Secure WebSocket over port 443
         */
        public val WSS: Protocol = Protocol("wss", 443)

        /**
         * Protocols by names map
         */
        public val byName: Map<String, Protocol> = listOf(HTTP, HTTPS, WS, WSS).associateBy { it.protocolName }

        /**
         * Parse a protocol scheme string into a [Protocol] instance
         */
        public fun parse(scheme: String): Protocol = byName[scheme.lowercase()] ?: Protocol(scheme, -1)
    }
}
