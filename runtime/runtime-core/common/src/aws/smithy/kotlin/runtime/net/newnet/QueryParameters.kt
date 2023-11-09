/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.newnet

import aws.smithy.kotlin.runtime.text.encoding.Encodable
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding

/**
 * Represents the parameters in a URL query string.
 */
public class QueryParameters private constructor(private val delegate: Map<Encodable, List<Encodable>>) : Map<Encodable, List<Encodable>> by delegate {
    public companion object {
        /**
         * Create new [QueryParameters] via a DSL builder block
         * @param block The code to apply to the builder
         * @return A new [QueryParameters] instance
         */
        public operator fun invoke(block: Builder.() -> Unit): QueryParameters = Builder().apply(block).build()

        private fun asDecoded(delegate: Map<Encodable, List<Encodable>>) = asString(delegate, Encodable::decoded)
        private fun asEncoded(delegate: Map<Encodable, List<Encodable>>) = asString(delegate, Encodable::encoded)

        private fun asString(delegate: Map<Encodable, List<Encodable>>, encodableForm: (Encodable) -> String) =
            delegate
                .entries
                .joinToString("&") { (key, values) ->
                    values.joinToString("&") { "${encodableForm(key)}=${encodableForm(it)}" }
                }

        /**
         * Parse a **decoded** query string into a [QueryParameters] instance
         * @param decoded A decoded query string
         * @return A new [QueryParameters] instance
         */
        public fun parseDecoded(decoded: String): QueryParameters = QueryParameters { parseDecoded(decoded) }

        /**
         * Parse an **encoded** query string into a [QueryParameters] instance
         * @param encoded An encoded query string
         * @return A new [QueryParameters] instance
         */
        public fun parseEncoded(encoded: String): QueryParameters = QueryParameters { parseEncoded(encoded) }
    }

    /**
     * Copy the properties of this [QueryParameters] instance into a new [Builder] object. Any changes to the builder
     * *will not* affect this instance.
     */
    public fun toBuilder(): Builder = Builder(delegate.mapValuesTo(mutableMapOf()) { (_, v) -> v.toMutableList() })

    override fun toString(): String = asEncoded(delegate)

    // TODO dsl functions for access to encoded/decoded param maps, likely requires some kind of MutableMapView similar
    //   to MutableListView
    /**
     * A mutable builder used to construct [QueryParameters] instances
     */
    public class Builder internal constructor(
        private val delegate: MutableMap<Encodable, MutableList<Encodable>>,
    ) : MutableMap<Encodable, MutableList<Encodable>> by delegate {
        /**
         * Initialize an empty [QueryParameters] builder
         */
        public constructor() : this(mutableMapOf())

        internal fun asDecoded() = asDecoded(delegate)
        internal fun asEncoded() = asEncoded(delegate)

        internal fun parseDecoded(decoded: String) = parse(decoded, PercentEncoding.Query::encodableFromDecoded)
        internal fun parseEncoded(encoded: String) = parse(encoded, PercentEncoding.Query::encodableFromEncoded)

        private fun parse(text: String, toEncodable: (String) -> Encodable) {
            clear()
            if (text.isNotEmpty()) {
                text.split("&").forEach { segment ->
                    val parts = segment.split("=")
                    val key = parts[0]
                    val value = when (parts.size) {
                        1 -> ""
                        2 -> parts[1]
                        else -> throw IllegalArgumentException("invalid query string segment $segment")
                    }
                    getOrPut(toEncodable(key), ::mutableListOf).add(toEncodable(value))
                }
            }
        }

        /**
         * Build a new [QueryParameters] from the currently-configured builder values
         * @return A new [QueryParameters] instance
         */
        public fun build(): QueryParameters = QueryParameters(delegate.mapValues { (_, v) -> v.toList() }.toMap())
    }
}
