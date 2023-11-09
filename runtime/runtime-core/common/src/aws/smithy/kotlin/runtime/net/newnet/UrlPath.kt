/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.newnet

import aws.smithy.kotlin.runtime.collections.MutableListView
import aws.smithy.kotlin.runtime.text.encoding.Encodable
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding

/**
 * Represents the path component of a URL
 * @param segments A list of path segments
 * @param trailingSlash Indicates whether a trailing slash is present in the path (e.g., "/foo/bar/" vs "/foo/bar")
 */
public class UrlPath private constructor(public val segments: List<Encodable>, public val trailingSlash: Boolean = false) {
    public companion object {
        /**
         * Create a new [UrlPath] via a DSL builder block
         * @param block The code to apply to the builder
         * @return A new [UrlPath] instance
         */
        public operator fun invoke(block: Builder.() -> Unit): UrlPath = Builder().apply(block).build()

        private fun asDecoded(segments: List<Encodable>, trailingSlash: Boolean) =
            asString(segments, trailingSlash, Encodable::decoded)

        private fun asEncoded(segments: List<Encodable>, trailingSlash: Boolean) =
            asString(segments, trailingSlash, Encodable::encoded)

        private fun asString(segments: List<Encodable>, trailingSlash: Boolean, encodableForm: (Encodable) -> String) = when {
            segments.isEmpty() -> if (trailingSlash) "/" else ""
            else -> segments.joinToString(
                separator = "/",
                prefix = "/",
                postfix = if (trailingSlash) "/" else "",
                transform = encodableForm,
            )
        }

        /**
         * Parse a **decoded** path string into a [UrlPath] instance
         * @param decoded A decoded path string
         * @return A new [UrlPath] instance
         */
        public fun parseDecoded(decoded: String): UrlPath = UrlPath { parseDecoded(decoded) }

        /**
         * Parse an **encoded** path string into a [UrlPath] instance
         * @param encoded An encoded path string
         * @return A new [UrlPath] instance
         */
        public fun parseEncoded(encoded: String): UrlPath = UrlPath { parseEncoded(encoded) }
    }

    /**
     * Copy the properties of this [UrlPath] instance into a new [Builder] object. Any changes to the builder *will not*
     * affect this instance.
     */
    public fun toBuilder(): Builder = Builder(this)

    override fun toString(): String = asEncoded(segments, trailingSlash)

    /**
     * A mutable builder used to construct [UrlPath] instances
     */
    public class Builder internal constructor(path: UrlPath?) {
        /**
         * Initialize an empty [UrlPath] builder
         */
        public constructor() : this(null)

        private val segments: MutableList<Encodable> = path?.segments?.toMutableList() ?: mutableListOf()

        /**
         * Remove all existing segments
         */
        public fun clearSegments() {
            segments.clear()
        }

        /**
         * A mutable list of **decoded** path segments. Any changes to this list will update the builder.
         */
        public val decodedSegments: MutableList<String> = MutableListView(
            segments,
            Encodable::decoded,
            PercentEncoding.Query::encodableFromDecoded,
        )

        /**
         * A mutable list of **encoded** path segments. Any changes to this list will update the builder.
         */
        public val encodedSegments: MutableList<String> = MutableListView(
            segments,
            Encodable::encoded,
            PercentEncoding.Query::encodableFromEncoded,
        )

        /**
         * Indicates whether a trailing slash is present in the path (e.g., "/foo/bar/" vs "/foo/bar")
         */
        public var trailingSlash: Boolean = path?.trailingSlash ?: false

        internal fun asDecoded() = asDecoded(segments, trailingSlash)
        internal fun asEncoded() = asEncoded(segments, trailingSlash)

        internal fun parseDecoded(decoded: String): Unit = parse(decoded, PercentEncoding.Path::encodableFromDecoded)
        internal fun parseEncoded(encoded: String): Unit = parse(encoded, PercentEncoding.Path::encodableFromEncoded)

        private fun parse(text: String, toEncodable: (String) -> Encodable) {
            segments.clear()

            when (text) {
                "" -> trailingSlash = false
                "/" -> trailingSlash = true
                else -> {
                    val noLeadingSlash = text.removePrefix("/")
                    trailingSlash = noLeadingSlash.endsWith('/')

                    val trimmed = if (trailingSlash) noLeadingSlash.removeSuffix("/") else noLeadingSlash
                    trimmed.split('/').mapTo(segments, toEncodable)
                }
            }
        }

        /**
         * Build a new [UrlPath] from the currently-configured builder values
         * @return A new [UrlPath] instance
         */
        public fun build(): UrlPath = UrlPath(segments.toList(), trailingSlash)
    }
}
