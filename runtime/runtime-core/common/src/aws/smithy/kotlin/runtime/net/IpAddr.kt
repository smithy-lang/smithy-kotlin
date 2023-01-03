/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * An IP Address (either IPv4 or IPv6)
 */
@InternalApi
public sealed class IpAddr {
    public companion object {
        /**
         * Parse a string into an [IpAddr]. Fails if [s] is not a valid IP address
         */
        public fun parse(s: String): IpAddr = when {
            s.isIpv4() -> Ipv4.parse(s)
            else -> Ipv6.parse(s)
        }
    }

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

            /**
             * Parse an IPv4 address from a string. Fails with [IllegalArgumentException] when given an invalid IPv4 address
             */
            public fun parse(s: String): Ipv4 = requireNotNull(s.parseIpv4OrNull()) { "Invalid Ipv4 address: $s" }
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

        /**
         * Convert this IPv4 address to an "IPv4-mapped" IPV6 address as described by
         * [RFC 4291 Section 2.5.5.2](https://datatracker.ietf.org/doc/html/rfc4291#section-2.5.5.2).
         * e.g. `::ffff:a.b.c.d`.
         *
         * See [Ipv6] documentation for more details.
         */
        public fun toMappedIpv6(): Ipv6 {
            val v6Octets = ByteArray(16)
            v6Octets[10] = 0xff.toByte()
            v6Octets[11] = 0xff.toByte()
            v6Octets[12] = octets[0]
            v6Octets[13] = octets[1]
            v6Octets[14] = octets[2]
            v6Octets[15] = octets[3]
            return Ipv6(v6Octets)
        }
    }

    /**
     * An IPv6 address as defined by [RFC 4291](https://www.rfc-editor.org/rfc/rfc4291).
     *
     * IPv6 addresses are defined as 128-bit integers represented as eight 16-bit segments.
     *
     * ## Textual representation
     *
     * ## IPv4-Mapped IPv6 Addresses
     *
     * IPv4-mapped IPv6 addresses are defined in
     * [RFC 4291 Section 2.5.5.2](https://datatracker.ietf.org/doc/html/rfc4291#section-2.5.5.2).
     *
     * The RFC describes the format of an "IPv4-Mapped IPv6 address" as follows:
     *
     * ```
     * |                80 bits               | 16 |      32 bits        |
     * +--------------------------------------+--------------------------+
     * |0000..............................0000|FFFF|    IPv4 address     |
     * +--------------------------------------+----+---------------------+
     * ```
     * So `::ffff:a.b.c.d` would be an IPv4-mapped IPv6 address representing the IPv4 address `a.b.c.d`.
     *
     * To convert an IPv4-mapped IPV6 address to IPv4 use [toIpv4Mapped]
     *
     * ## IPv4-Compatible IPv6 Addresses
     *
     * **NOTE**: IPv4-compatible addresses have been officially deprecated.
     *
     * IPv4-compatible IPv6 addresses are defined in
     * [RFC 4291 Section 2.5.5.1](https://datatracker.ietf.org/doc/html/rfc4291#section-2.5.5.1).
     *
     * The RFC describes the format of an "IPv4-Compatible IPv6 address" as follows:
     *
     * ```text
     * |                80 bits               | 16 |      32 bits        |
     * +--------------------------------------+--------------------------+
     * |0000..............................0000|0000|    IPv4 address     |
     * +--------------------------------------+----+---------------------+
     * ```
     * So `::a.b.c.d` would be an IPv4-compatible IPv6 address representing the IPv4 address `a.b.c.d`.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    public data class Ipv6(
        /**
         * The sixteen eight-bit integers the IPv6 address consists of
         */
        override val octets: ByteArray,

        /**
         * Scoped IPv6 address zone identifier as defined in [RFC 6874](https://www.rfc-editor.org/rfc/rfc6874).
         * Scoped IPv6 addresses are described in [RFC 4007](https://www.rfc-editor.org/rfc/rfc4007)
         */
        public val zoneId: String? = null,
    ) : IpAddr() {

        init {
            require(octets.size == 16) { "Invalid IPv6 repr: $octets; expected 16 bytes" }
        }

        /**
         * Creates a new IPv6 address from eight 16-bit segments and an optional zone identifier.
         * The result will represent the IP address a:b:c:d:e:f:g:h.
         */
        public constructor(
            a: UShort,
            b: UShort,
            c: UShort,
            d: UShort,
            e: UShort,
            f: UShort,
            g: UShort,
            h: UShort,
            zoneId: String? = null,
        ) : this(ipv6SegmentsToOctets(a, b, c, d, e, f, g, h), zoneId)

        public companion object {
            /**
             * An IPv6 address representing localhost: ::1
             */
            public val LOCALHOST: Ipv6 = Ipv6(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))

            /**
             * An IPv6 address representing the unspecified address: `::`
             */
            public val UNSPECIFIED: Ipv6 = Ipv6(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))

            /**
             * Parse an IPv6 address from a string. Fails with [IllegalArgumentException] when given an invalid IPv6 address
             *
             * NOTE: This does not handle zone identifiers
             */
            public fun parse(s: String): Ipv6 = requireNotNull(s.parseIpv6OrNull()) { "Invalid Ipv6 address: $s" }
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
            val ipv4Mapped = toIpv4Mapped()
            val formatted = when {
                isLoopBack -> "::1"
                isUnspecified -> "::"
                ipv4Mapped != null -> "::ffff:$ipv4Mapped"
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

            if (zoneId != null) {
                "$formatted%$zoneId"
            } else {
                formatted
            }
        }

        private fun StringBuilder.formatSegments(range: IntRange) {
            if (range.first >= segments.size || range.isEmpty()) return
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
            get() = when (isMulticast) {
                false -> null
                true -> when ((segments[0] and 0x000fu).toUInt()) {
                    1u -> Ipv6MulticastScope.InterfaceLocal
                    2u -> Ipv6MulticastScope.LinkLocal
                    3u -> Ipv6MulticastScope.RealmLocal
                    4u -> Ipv6MulticastScope.AdminLocal
                    5u -> Ipv6MulticastScope.SiteLocal
                    8u -> Ipv6MulticastScope.OrganizationLocal
                    14u -> Ipv6MulticastScope.Global
                    else -> null
                }
            }

        /**
         * Returns true if this is a multicast address as defined by
         * [RFC 4291 2.7](https://www.rfc-editor.org/rfc/rfc4291#section-2.7).
         */
        public val isMulticast: Boolean
            get() = segments[0] and 0xff00u == 0xff00u.toUShort()

        override fun toString(): String = address

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Ipv6

            if (!octets.contentEquals(other.octets)) return false

            return true
        }

        override fun hashCode(): Int = octets.contentHashCode()

        /**
         * Try to convert this address to an [Ipv4] address if it is an
         * [Ipv4-mapped](https://tools.ietf.org/html/rfc4291#section-2.5.5.2) address.
         *
         * Returns `null if this address is not an IPv4 mapped address.
         */
        public fun toIpv4Mapped(): Ipv4? {
            val prefix = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff.toByte(), 0xff.toByte())
            prefix.forEachIndexed { idx, byte ->
                if (octets[idx] != byte) return null
            }

            return Ipv4(octets.sliceArray(prefix.size until octets.size))
        }
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
