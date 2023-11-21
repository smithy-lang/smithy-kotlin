/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.text.encoding

/**
 * An immutable encapsulation of data in its original (decoded) format, an encoding, and the data encoded in that
 * format.
 * @param decoded The decoded data
 * @param encoded The encoded data
 * @param encoding The encoding used to encode the data
 */
public class Encodable internal constructor(
    public val decoded: String,
    public val encoded: String,
    public val encoding: Encoding,
) {
    public companion object {
        /**
         * An empty encodable, containing empty decoded/encoded data in no encoding format
         */
        public val Empty: Encodable = Encodable("", "", Encoding.None)
    }

    /**
     * Indicates whether this [Encodable] has an empty [decoded] and [encoded] representation
     */
    public val isEmpty: Boolean = decoded.isEmpty() && encoded.isEmpty()

    /**
     * Indicates whether this [Encodable] has a non-empty [decoded] or [encoded] representation
     */
    public val isNotEmpty: Boolean = !isEmpty

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Encodable) return false

        if (decoded != other.decoded) return false
        if (encoded != other.encoded) return false
        return encoding == other.encoding
    }

    override fun hashCode(): Int {
        var result = decoded.hashCode()
        result = 31 * result + encoded.hashCode()
        result = 31 * result + encoding.hashCode()
        return result
    }

    /**
     * Returns a new [Encodable] derived from re-encoding this instance's [decoded] data. This _may_ be different from
     * the current instance's [encoded] data if the object was created with a non-canonical encoding.
     */
    public fun reencode(): Encodable = encoding.encodableFromDecoded(decoded)

    override fun toString(): String = buildString {
        append("Encodable(decoded=")
        append(decoded)
        append(", encoded=")
        append(encoded)
        append(", encoding=")
        append(encoding.name)
        append(")")
    }
}
