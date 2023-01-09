/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

/**
 * An IPv4 address as defined by [RFC 791](https://www.rfc-editor.org/rfc/rfc791)
 * @param octets The four eight-bit integers that make up this address
 */
public data class IpV4Addr(
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
        public val LOCALHOST: IpV4Addr = IpV4Addr(127u, 0u, 0u, 1u)

        /**
         * An Ipv4 address for the special "unspecified" address (`0.0.0.0`). Also known as
         * `INADDR_ANY`, see [ip7](https://man7.org/linux/man-pages/man7/ip.7.html)
         */
        public val UNSPECIFIED: IpV4Addr = IpV4Addr(0u, 0u, 0u, 0u)

        /**
         * Parse an IPv4 address from a string. Fails with [IllegalArgumentException] when given an invalid IPv4 address
         */
        public fun parse(s: String): IpV4Addr = requireNotNull(s.parseIpv4OrNull()) { "Invalid Ipv4 address: $s" }
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
    public override val isUnspecified: Boolean
        get() = this == UNSPECIFIED

    override val address: String
        get() = octets.joinToString(separator = ".") { it.toUByte().toString() }

    override fun toString(): String = address

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as IpV4Addr

        if (!octets.contentEquals(other.octets)) return false

        return true
    }

    override fun hashCode(): Int = octets.contentHashCode()

    /**
     * Convert this IPv4 address to an "IPv4-mapped" IPV6 address as described by
     * [RFC 4291 Section 2.5.5.2](https://datatracker.ietf.org/doc/html/rfc4291#section-2.5.5.2).
     * e.g. `::ffff:a.b.c.d`.
     *
     * See [IpV6Addr] documentation for more details.
     */
    public fun toMappedIpv6(): IpV6Addr {
        val v6Octets = ByteArray(16)
        v6Octets[10] = 0xff.toByte()
        v6Octets[11] = 0xff.toByte()
        v6Octets[12] = octets[0]
        v6Octets[13] = octets[1]
        v6Octets[14] = octets[2]
        v6Octets[15] = octets[3]
        return IpV6Addr(v6Octets)
    }
}
