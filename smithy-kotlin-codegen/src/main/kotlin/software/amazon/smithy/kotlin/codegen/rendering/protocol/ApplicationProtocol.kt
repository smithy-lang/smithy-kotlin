/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import java.util.*

/**
 * Represents the resolves [Symbol]s and references for an
 * application protocol (e.g., "http", "mqtt", etc).
 *
 * @param name The protocol name (e.g., http, mqtt, etc).
 * @param requestType The type used to represent request messages for the protocol.
 * @param responseType The type used to represent response messages for the protocol.
 */
class ApplicationProtocol(
    /**
     * Gets the protocol name.
     *
     *
     * All HTTP protocols should start with "http".
     * All MQTT protocols should start with "mqtt".
     *
     * @return Returns the protocol name.
     */
    val name: String,

    /**
     * Gets the symbol used to refer to the request type for this protocol.
     *
     * @return Returns the protocol request type.
     */
    val requestType: Symbol,

    /**
     * Gets the symbol used to refer to the response type for this protocol.
     *
     * @return Returns the protocol response type.
     */
    val responseType: Symbol
) {

    /**
     * Checks if the protocol is an HTTP based protocol.
     *
     * @return Returns true if it is HTTP based.
     */
    val isHttpProtocol: Boolean
        get() = name.startsWith("http")

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is ApplicationProtocol) {
            return false
        }
        return (
            requestType == other.requestType &&
                responseType == other.responseType
            )
    }

    override fun hashCode(): Int = Objects.hash(requestType, responseType)

    companion object {
        /**
         * Creates a default HTTP application protocol.
         *
         * @return Returns the created application protocol.
         */
        fun createDefaultHttpApplicationProtocol(): ApplicationProtocol = ApplicationProtocol(
            "http",
            createHttpSymbol("HttpRequestBuilder", "request"),
            createHttpSymbol("HttpResponse", "response")
        )

        private fun createHttpSymbol(symbolName: String, subnamespace: String): Symbol = Symbol
            .builder()
            .name(symbolName)
            .namespace("${KotlinDependency.CLIENT_RT_HTTP.namespace}.$subnamespace", ".")
            .addDependency(KotlinDependency.CLIENT_RT_HTTP)
            .build()
    }
}
