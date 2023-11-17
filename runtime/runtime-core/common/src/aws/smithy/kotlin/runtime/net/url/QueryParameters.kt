/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import aws.smithy.kotlin.runtime.collections.MultiMap
import aws.smithy.kotlin.runtime.collections.MutableMultiMap
import aws.smithy.kotlin.runtime.collections.mutableMultiMapOf
import aws.smithy.kotlin.runtime.collections.views.asView
import aws.smithy.kotlin.runtime.text.encoding.Encodable
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding

/**
 * Represents the parameters in a URL query string.
 */
public class QueryParameters private constructor(
    private val delegate: MultiMap<Encodable, Encodable>,

    /**
     * A flag indicating whether to force inclusion of the `?` query separator even when there are no parameters (e.g.,
     * `http://foo.com?` vs `http://foo.com`).
     */
    public val forceQuery: Boolean,
) : MultiMap<Encodable, Encodable> by delegate {
    public companion object {
        /**
         * No query parameters
         */
        public val Empty: QueryParameters = QueryParameters { }

        /**
         * Create new [QueryParameters] via a DSL builder block
         * @param block The code to apply to the builder
         * @return A new [QueryParameters] instance
         */
        public operator fun invoke(block: Builder.() -> Unit): QueryParameters = Builder().apply(block).build()

        private fun asDecoded(values: Sequence<Map.Entry<Encodable, Encodable>>, forceQuery: Boolean) =
            asString(values, forceQuery, Encodable::decoded)

        private fun asEncoded(values: Sequence<Map.Entry<Encodable, Encodable>>, forceQuery: Boolean) =
            asString(values, forceQuery, Encodable::encoded)

        private fun asString(
            values: Sequence<Map.Entry<Encodable, Encodable>>,
            forceQuery: Boolean,
            encodableForm: (Encodable) -> String,
        ) =
            values
                .joinToString(
                    separator = "&",
                    prefix = if (forceQuery || values.any()) "?" else "",
                ) { (key, value) -> "${encodableForm(key)}=${encodableForm(value)}" }

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
    public fun toBuilder(): Builder = Builder(delegate.toMutableMultiMap(), forceQuery)

    public val decodedParameters: MultiMap<String, String>
        get() = asView(
            Encodable::decoded,
            PercentEncoding.Query::encodableFromDecoded,
            Encodable::decoded,
            PercentEncoding.Query::encodableFromDecoded,
        )

    public val encodedParameters: MultiMap<String, String>
        get() = asView(
            Encodable::encoded,
            PercentEncoding.Query::encodableFromEncoded,
            Encodable::encoded,
            PercentEncoding.Query::encodableFromEncoded,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as QueryParameters

        if (delegate != other.delegate) return false
        if (forceQuery != other.forceQuery) return false

        return true
    }

    override fun hashCode(): Int {
        var result = delegate.hashCode()
        result = 31 * result + forceQuery.hashCode()
        return result
    }

    override fun toString(): String = asEncoded(delegate.entryValues, forceQuery)

    /**
     * A mutable builder used to construct [QueryParameters] instances
     */
    public class Builder internal constructor(
        private val delegate: MutableMultiMap<Encodable, Encodable>,

        /**
         * A flag indicating whether to force inclusion of the `?` query separator even when there are no parameters
         * (e.g., `http://foo.com?` vs `http://foo.com`).
         */
        public var forceQuery: Boolean = false,
    ) : MutableMultiMap<Encodable, Encodable> by delegate {
        /**
         * Initialize an empty [QueryParameters] builder
         */
        public constructor() : this(mutableMultiMapOf())

        /**
         * Get or set the query parameters as a **decoded** string.
         */
        public var decoded: String
            get() = asDecoded(delegate.entryValues, forceQuery)
            set(value) { parseDecoded(value) }

        /**
         * Get or set the query parameters as an **encoded** string.
         */
        public var encoded: String
            get() = asEncoded(delegate.entryValues, forceQuery)
            set(value) { parseEncoded(value) }

        internal fun parse(value: String, encoding: UrlEncoding): Unit =
            if (UrlEncoding.QueryParameters in encoding) parseEncoded(value) else parseDecoded(value)

        internal fun parseDecoded(decoded: String) = parseInto(decodedParameters, decoded)
        internal fun parseEncoded(encoded: String) = parseInto(encodedParameters, encoded)

        private fun parseInto(map: MutableMultiMap<String, String>, text: String) {
            clear()

            forceQuery = text == "?"
            val params = text.removePrefix("?")

            if (params.isNotEmpty()) {
                params
                    .split("&")
                    .map { segment ->
                        val parts = segment.split("=")
                        val key = parts[0]
                        val value = when (parts.size) {
                            1 -> ""
                            2 -> parts[1]
                            else -> throw IllegalArgumentException("invalid query string segment $segment")
                        }
                        key to value
                    }
                    .groupByTo(map, Pair<String, String>::first, Pair<String, String>::second)
            }
        }

        public val decodedParameters: MutableMultiMap<String, String> = asView(
            Encodable::decoded,
            PercentEncoding.Query::encodableFromDecoded,
            Encodable::decoded,
            PercentEncoding.Query::encodableFromDecoded,
        )

        public fun decodedParameters(block: MutableMultiMap<String, String>.() -> Unit) {
            decodedParameters.apply(block)
        }

        public val encodedParameters: MutableMultiMap<String, String> = asView(
            Encodable::encoded,
            PercentEncoding.Query::encodableFromEncoded,
            Encodable::encoded,
            PercentEncoding.Query::encodableFromEncoded,
        )

        public fun encodedParameters(block: MutableMultiMap<String, String>.() -> Unit) {
            encodedParameters.apply(block)
        }

        /**
         * Build a new [QueryParameters] from the currently-configured builder values
         * @return A new [QueryParameters] instance
         */
        public fun build(): QueryParameters = QueryParameters(delegate.toMultiMap(), forceQuery)

        public fun copyFrom(other: QueryParameters) {
            clear()
            other.mapValuesTo(this) { (_, values) ->
                values.toMutableList() // Copy the mutable list to a new mutable list
            }
            forceQuery = other.forceQuery
        }

        public fun copyFrom(other: Builder) {
            clear()
            other.mapValuesTo(this) { (_, values) ->
                values.toMutableList() // Copy the mutable list to a new mutable list
            }
            forceQuery = other.forceQuery
        }
    }
}
