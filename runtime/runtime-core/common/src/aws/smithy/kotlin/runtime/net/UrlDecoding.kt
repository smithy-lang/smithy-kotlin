/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

/**
 * Identifies the type of decoding behavior desired when parsing a URL.
 */
public sealed class UrlDecoding(private val mask: Int) {
    /**
     * Do not URL-decode any part of the given URL string
     */
    public object DecodeNone : UrlDecoding(0) {
        override fun toString(): String = "DecodeNone"
    }

    /**
     * URL-decode the path of the given URL string
     */
    public object DecodePath : UrlDecoding(1) {
        override fun toString(): String = "DecodePath"
    }

    /**
     * URL-decode the query parameters of the given URL string
     */
    public object DecodeQueryParameters : UrlDecoding(2) {
        override fun toString(): String = "DecodeQueryParameters"
    }

    /**
     * URL-decode the fragment of the given URL string
     */
    public object DecodeFragment : UrlDecoding(4) {
        override fun toString(): String = "DecodeFragment"
    }

    private class Composite(mask: Int) : UrlDecoding(mask)

    public operator fun plus(other: UrlDecoding): UrlDecoding = Composite(mask or other.mask)
    public operator fun minus(other: UrlDecoding): UrlDecoding = Composite(mask and other.mask.inv())

    override fun equals(other: Any?): Boolean = other is UrlDecoding && mask == other.mask
    override fun hashCode(): Int = mask
    override fun toString(): String = entries.filter(::contains).joinToString("|")

    public operator fun contains(item: UrlDecoding): Boolean = mask and item.mask != 0

    public companion object {
        /**
         * Gets a collection of all the individual URL decoding behaviors
         */
        public val entries: Set<UrlDecoding> = setOf(DecodePath, DecodeQueryParameters, DecodeFragment)

        /**
         * URL-decode all parts of the given URL string
         */
        public val DecodeAll: UrlDecoding = Composite(entries.sumOf { it.mask })
    }
}
