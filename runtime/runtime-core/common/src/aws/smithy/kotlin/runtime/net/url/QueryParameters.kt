/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import aws.smithy.kotlin.runtime.text.encoding.Encodable
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding

/**
 * Represents the parameters in a URL query string.
 */
public class QueryParameters private constructor(
    private val delegate: Map<Encodable, List<Encodable>>,

    /**
     * A flag indicating whether to force inclusion of the `?` query separator even when there are no parameters (e.g.,
     * `http://foo.com?` vs `http://foo.com`).
     */
    public val forceQuery: Boolean,
) : Map<Encodable, List<Encodable>> by delegate {
    public companion object {
        /**
         * Create new [QueryParameters] via a DSL builder block
         * @param block The code to apply to the builder
         * @return A new [QueryParameters] instance
         */
        public operator fun invoke(block: Builder.() -> Unit): QueryParameters = Builder().apply(block).build()

        private fun asDecoded(delegate: Map<Encodable, List<Encodable>>, forceQuery: Boolean) =
            asString(delegate, forceQuery, Encodable::decoded)

        private fun asEncoded(delegate: Map<Encodable, List<Encodable>>, forceQuery: Boolean) =
            asString(delegate, forceQuery, Encodable::encoded)

        private fun asString(
            delegate: Map<Encodable, List<Encodable>>,
            forceQuery: Boolean,
            encodableForm: (Encodable) -> String,
        ) =
            delegate
                .entries
                .joinToString(
                    separator = "&",
                    prefix = if (forceQuery || delegate.isNotEmpty()) "?" else "",
                ) { (key, values) ->
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
    public fun toBuilder(): Builder = Builder(
        delegate.mapValuesTo(mutableMapOf()) { (_, v) -> v.toMutableList() },
        forceQuery,
    )

    override fun toString(): String = asEncoded(delegate, forceQuery)

    // TODO dsl functions for access to encoded/decoded param maps, likely requires some kind of MutableMapView similar
    //   to MutableListView
    /**
     * A mutable builder used to construct [QueryParameters] instances
     */
    public class Builder internal constructor(
        private val delegate: MutableMap<Encodable, MutableList<Encodable>>,

        /**
         * A flag indicating whether to force inclusion of the `?` query separator even when there are no parameters
         * (e.g., `http://foo.com?` vs `http://foo.com`).
         */
        public var forceQuery: Boolean = false,
    ) : MutableMap<Encodable, MutableList<Encodable>> by delegate {
        /**
         * Initialize an empty [QueryParameters] builder
         */
        public constructor() : this(mutableMapOf())

        /**
         * Get or set the query parameters as a **decoded** string.
         */
        public var decoded: String
            get() = asDecoded(delegate, forceQuery)
            set(value) { parseDecoded(value) }

        /**
         * Get or set the query parameters as an **encoded** string.
         */
        public var encoded: String
            get() = asEncoded(delegate, forceQuery)
            set(value) { parseEncoded(value) }

        internal fun parseDecoded(decoded: String) = parse(decoded, PercentEncoding.Query::encodableFromDecoded)
        internal fun parseEncoded(encoded: String) = parse(encoded, PercentEncoding.Query::encodableFromEncoded)

        private fun parse(text: String, toEncodable: (String) -> Encodable) {
            clear()

            forceQuery = text == "?"
            val params = text.removePrefix("?")

            if (params.isNotEmpty()) {
                params.split("&").forEach { segment ->
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
        public fun build(): QueryParameters = QueryParameters(
            delegate.mapValues { (_, v) -> v.toList() }.toMap(),
            forceQuery,
        )
    }
}
