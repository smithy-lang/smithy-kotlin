/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

/**
 * Identifies the components of a URL which are in an already-encoded form.
 */
public sealed class UrlEncoding(private val mask: Int) {
    /**
     * None of the encodable parts of the URL are encoded.
     */
    public object None : UrlEncoding(0) {
        override fun toString(): String = "None"
    }

    /**
     * The path of the URL is in an encoded form.
     */
    public object Path : UrlEncoding(1) {
        override fun toString(): String = "Path"
    }

    /**
     * The query parameters of the URL are in an encoded form.
     */
    public object QueryParameters : UrlEncoding(2) {
        override fun toString(): String = "QueryParameters"
    }

    /**
     * The fragment of the URL is in an encoded form.
     */
    public object Fragment : UrlEncoding(4) {
        override fun toString(): String = "Fragment"
    }

    private class Composite(mask: Int) : UrlEncoding(mask)

    public operator fun plus(other: UrlEncoding): UrlEncoding = Composite(mask or other.mask)
    public operator fun minus(other: UrlEncoding): UrlEncoding = Composite(mask and other.mask.inv())

    override fun equals(other: Any?): Boolean = other is UrlEncoding && mask == other.mask
    override fun hashCode(): Int = mask
    override fun toString(): String = entries.filter(::contains).joinToString("|")

    public operator fun contains(item: UrlEncoding): Boolean = mask and item.mask != 0

    public companion object {
        /**
         * Gets a collection of all the individual URL encoding behaviors
         */
        public val entries: Set<UrlEncoding> = setOf(Path, QueryParameters, Fragment)

        /**
         * All parts of the URL are encoded.
         */
        public val All: UrlEncoding = Composite(entries.sumOf(UrlEncoding::mask))
    }
}
