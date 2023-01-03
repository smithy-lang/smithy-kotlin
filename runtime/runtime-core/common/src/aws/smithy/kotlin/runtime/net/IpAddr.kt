/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.util.InternalApi

// TODO - String.toIpAddr(), toIpv4Addr(), toIpv6Addr(), toIpAddrOrNull() etc

/**
 * An IP Address (either IPv4 or IPv6)
 */
@InternalApi
public sealed class IpAddr {
    /**
     * The raw numerical address
     */
    public abstract val octets: ByteArray

    /**
     * The resolved numerical address represented as a string
     */
    public abstract val address: String

    /**
     * Returns true if this is the loopback address
     */
    public abstract val isLoopBack: Boolean

    /**
     * True if this is an [Ipv4] instance
     */
    public val isIpv4: Boolean
        get() = this is Ipv4

    /**
     * True if this is an [Ipv6] instance
     */
    public val isIpv6: Boolean
        get() = this is Ipv6

    /**
     * An IPv4 address as defined by [RFC 791](https://www.rfc-editor.org/rfc/rfc791)
     */
    public data class Ipv4(
        /**
         * The four eight-bit integers that make up this address
         */
        override val octets: ByteArray,
    ) : IpAddr() {

        /**
         * Creates a new IPv4 address from four eight-bit octets. The result
         * represents the IP address `a.b.c.d`
         */
        public constructor(a: UByte, b: UByte, c: UByte, d: UByte) : this(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

        init {
            require(octets.size == 4) { "Invalid IPv4 repr: $octets; expected 4 bytes" }
        }

        public companion object {
            /**
             * An IPv4 address with the address pointing to localhost: 127.0.0.1
             */
            public val LOCALHOST: Ipv4 = Ipv4(127u, 0u, 0u, 1u)

            /**
             * An Ipv4 address for the special "unspecified" address (`0.0.0.0`). Also known as
             * `INADDR_ANY`, see [ip7](https://man7.org/linux/man-pages/man7/ip.7.html)
             */
            public val UNSPECIFIED: Ipv4 = Ipv4(0u, 0u, 0u, 0u)
        }

        /**
         * Returns true if this is a loopback address (127.0.0.0/8).
         * Defined by [RFC 1122](https://www.rfc-editor.org/rfc/rfc1122)
         */
        override val isLoopBack: Boolean
            get() = octets[0] == 127.toByte()

        /**
         * Returns true if this is the "any" address (`0.0.0.0`)
         */
        public val isUnspecified: Boolean
            get() = this == UNSPECIFIED

        override val address: String
            get() = octets.joinToString(separator = ".") { it.toUByte().toString() }

        override fun toString(): String = address

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Ipv4

            if (!octets.contentEquals(other.octets)) return false

            return true
        }

        override fun hashCode(): Int = octets.contentHashCode()
    }

    /**
     * An IPv6 address as defined by [RFC 4291](https://www.rfc-editor.org/rfc/rfc4291)
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    public data class Ipv6(
        /**
         * The sixteen eight-bit integers the IPv6 address consists of
         */
        override val octets: ByteArray,
    ) : IpAddr() {

        init {
            require(octets.size == 16) { "Invalid IPv6 repr: $octets; expected 16 bytes" }
        }

        /**
         * Creates a new IPv6 address from eight 16-bit segments.
         * The result will represent the IP address a:b:c:d:e:f:g:h.
         */
        public constructor(a: UShort, b: UShort, c: UShort, d: UShort, e: UShort, f: UShort, g: UShort, h: UShort) :
            this(ipv6SegmentsToOctets(a, b, c, d, e, f, g, h))

        public companion object {
            /**
             * An IPv6 address representing localhost: ::1
             */
            public val LOCALHOST: Ipv6 = Ipv6(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))

            /**
             * An IPv6 address representing the unspecified address: `::`
             */
            public val UNSPECIFIED: Ipv6 = Ipv6(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        }

        /**
         * Return the eight 16-bit segments that make up this address
         */
        public val segments: UShortArray by lazy {
            UShortArray(octets.size / 2) {
                octets.readUShort(it * 2)
            }
        }

        override val address: String by lazy {
            when {
                isLoopBack -> "::1"
                isUnspecified -> "::"
                // TODO - ipv4 compatible/mapped address
                else -> buildString {

                    // find first 0 segment
                    data class Span(var start: Int = 0, var len: Int = 0)
                    val zeroes = run {
                        var curr = Span()
                        var longest = Span()
                        segments.forEachIndexed { idx, segment ->
                            if (segment == 0.toUShort()) {
                                if (curr.len == 0) curr.start = idx

                                curr.len += 1

                                if (curr.len > longest.len) longest = curr
                            } else {
                                curr = Span()
                            }
                        }
                        longest
                    }

                    if (zeroes.len > 1) {
                        formatSegments(0 until zeroes.start)
                        append("::")
                        formatSegments((zeroes.start + zeroes.len) until segments.size)
                    } else {
                        formatSegments(segments.indices)
                    }
                }
            }
        }

        private fun StringBuilder.formatSegments(range: IntRange) {
            if (range.first >= segments.size) return
            append(segments[range.first].toString(16))
            val tail = IntRange(range.first + 1, range.last)
            for (i in tail) {
                append(':')
                append(segments[i].toString(16))
            }
        }

        /**
         * Returns true if this is the loopback address (`::1`), as defined in
         * [RFC 4291 section 2.5.3.](https://www.rfc-editor.org/rfc/rfc4291#section-2.5.3)
         */
        override val isLoopBack: Boolean
            get() = this == LOCALHOST

        /**
         * Returns true if this is the "any" address (`::`)
         */
        public val isUnspecified: Boolean
            get() = this == UNSPECIFIED

        /**
         * Returns the multicast scope of the address if it is a multicast address
         */
        public val multicastScope: Ipv6MulticastScope?
            get() = TODO("not implemented yet")

        override fun toString(): String = address

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Ipv6

            if (!octets.contentEquals(other.octets)) return false

            return true
        }

        override fun hashCode(): Int = octets.contentHashCode()
    }
}

/**
 * Scope of an [IpAddr.Ipv6] multicast address as defined in
 * [RFC 7346 section 2](https://tools.ietf.org/html/rfc7346#section-2)
 */
public enum class Ipv6MulticastScope {
    InterfaceLocal,
    LinkLocal,
    RealmLocal,
    AdminLocal,
    SiteLocal,
    OrganizationLocal,
    Global,
}

// Big-endian octets
private fun ipv6SegmentsToOctets(
    a: UShort,
    b: UShort,
    c: UShort,
    d: UShort,
    e: UShort,
    f: UShort,
    g: UShort,
    h: UShort,
): ByteArray = ByteArray(16).apply {
    writeUShort(a, 0)
    writeUShort(b, 2)
    writeUShort(c, 4)
    writeUShort(d, 6)
    writeUShort(e, 8)
    writeUShort(f, 10)
    writeUShort(g, 12)
    writeUShort(h, 14)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun ByteArray.writeUShort(value: UShort, startIdx: Int) {
    val data = this
    val x = value.toInt()
    data[startIdx] = (x ushr 8 and 0xff).toByte()
    data[startIdx + 1] = (x and 0xff).toByte()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun ByteArray.readUShort(idx: Int): UShort {
    val data = this
    check(idx <= data.size - 2)
    val s = data[idx].toInt() and 0xff shl 8 or (data[idx + 1].toInt() and 0xff)
    return s.toUShort()
}
