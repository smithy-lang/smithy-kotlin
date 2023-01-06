/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

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
public data class IpV6Addr(
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
        public val LOCALHOST: IpV6Addr = IpV6Addr(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))

        /**
         * An IPv6 address representing the unspecified address: `::`
         */
        public val UNSPECIFIED: IpV6Addr = IpV6Addr(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))

        /**
         * The prefix all IPv4-mapped IPv6 address have
         */
        internal val IPV4_MAPPED_PREFIX_OCTETS: ByteArray = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff.toByte(), 0xff.toByte())

        /**
         * Parse an IPv6 address from a string. Fails with [IllegalArgumentException] when given an invalid IPv6 address
         *
         * NOTE: This does not handle zone identifiers
         */
        public fun parse(s: String): IpV6Addr = requireNotNull(s.parseIpv6OrNull()) { "Invalid Ipv6 address: $s" }
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

    private fun StringBuilder.formatSegments(range: IntRange) =
        range.joinTo(this, separator = ":") { segments[it].toString(16) }

    /**
     * Returns true if this is the loopback address (`::1`), as defined in
     * [RFC 4291 section 2.5.3.](https://www.rfc-editor.org/rfc/rfc4291#section-2.5.3)
     */
    override val isLoopBack: Boolean
        get() = this == LOCALHOST

    /**
     * Returns true if this is the "any" address (`::`)
     */
    public override val isUnspecified: Boolean
        get() = this == UNSPECIFIED

    override fun toString(): String = address

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as IpV6Addr

        if (!octets.contentEquals(other.octets)) return false

        return true
    }

    override fun hashCode(): Int = octets.contentHashCode()

    /**
     * Try to convert this address to an [Ipv4] address if it is an
     * [Ipv4-mapped](https://tools.ietf.org/html/rfc4291#section-2.5.5.2) address.
     *
     * Returns `null` if this address is not an IPv4 mapped address.
     */
    public fun toIpv4Mapped(): IpV4Addr? {
        IPV4_MAPPED_PREFIX_OCTETS.forEachIndexed { idx, byte ->
            if (octets[idx] != byte) return null
        }

        return IpV4Addr(octets.sliceArray(IPV4_MAPPED_PREFIX_OCTETS.size until octets.size))
    }
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
