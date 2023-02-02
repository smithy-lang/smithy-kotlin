/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Validates a hostname per [RFC 1123](https://www.ietf.org/rfc/rfc1123.txt).
 */
@InternalApi
public fun String.isValidHostname(): Boolean =
    length in 1..63 && this[0].isLetterOrDigit() && drop(1).all { it.isLetterOrDigit() || it == '-' }

@InternalApi
public fun String.isIpv4(): Boolean = parseIpv4OrNull() != null

internal fun String.parseIpv4OrNull(): IpV4Addr? {
    val segments = split('.')
    if (segments.size != 4 || segments.any { (it.toIntOrNull() ?: -1) !in 0..255 }) return null
    // NOTE: toByte() will fail range validation checks, need to go through UByte first
    val octets = segments.map { it.toUByte().toByte() }.toByteArray()
    return IpV4Addr(octets)
}

/**
 * Validates a string as an IPv6 address according to [RFC 4291 §2.2](https://www.rfc-editor.org/rfc/rfc4291.html#section-2.2).
 * Does NOT validate for [address prefixes (§2.3)](https://www.rfc-editor.org/rfc/rfc4291.html#section-2.3).
 *
 * The address MAY include a scope ID which is validated per [RFC 4007](https://www.rfc-editor.org/rfc/rfc4007#section-11.2).
 */
@InternalApi
public fun String.isIpv6(): Boolean = parseIpv6OrNull() != null

private const val IPV6_SEGMENT_COUNT = 8
private const val IPV4_MAPPED_IPV6_SEGMENT_COUNT = 7

private fun String.getIpv6AddressSegments(): List<String>? {
    val explicitSegmentGroups = split("::")
    if (explicitSegmentGroups.size > 2) {
        return null
    }
    if (explicitSegmentGroups.size == 1) { // this would mean the address is explicit as-written
        return explicitSegmentGroups[0].split(":")
    }

    val leftSegments = if (explicitSegmentGroups[0] == "") emptyList() else explicitSegmentGroups[0].split(':')
    val rightSegments = if (explicitSegmentGroups[1] == "") emptyList() else explicitSegmentGroups[1].split(':')

    // double colon with full explicit segments
    if (leftSegments.size + rightSegments.size == IPV6_SEGMENT_COUNT) return null

    // IPv4-mapped address of form `::ffff:a.b.c.d` -> `0:0:0:0:0:ffff:a.b.c.d`
    val totalSegmentCount = if (rightSegments.lastOrNull()?.contains('.') == true) IPV4_MAPPED_IPV6_SEGMENT_COUNT else IPV6_SEGMENT_COUNT
    val implicitSegmentCount = totalSegmentCount - leftSegments.size - rightSegments.size

    return buildList {
        addAll(leftSegments)
        repeat(implicitSegmentCount) { add("0") }
        addAll(rightSegments)
    }
}

/**
 * Parse the current string as an IPv6 address
 */
internal fun String.parseIpv6OrNull(): IpV6Addr? {
    val components = split('%')
    if (components.size > 2) return null
    if (components.size == 2 && !components[1].isIpv6ZoneId()) return null
    val zoneId = if (components.size == 2) components[1] else null

    val segments = components[0].getIpv6AddressSegments() ?: return null
    if (segments.size < IPV4_MAPPED_IPV6_SEGMENT_COUNT) return null

    // the "common" segments MUST be valid IPv6
    for (i in 0 until IPV4_MAPPED_IPV6_SEGMENT_COUNT - 1) {
        if (!segments[i].isIpv6AddressSegment()) return null
    }

    // if this is an IPv4-mapped IPv6 address, the last segment MUST be IPv4 AND must contain the prefix
    // see https://datatracker.ietf.org/doc/html/rfc4291#section-2.5.5.2
    if (segments.size == IPV4_MAPPED_IPV6_SEGMENT_COUNT) {
        val prefix = segments.subList(0, IPV4_MAPPED_IPV6_SEGMENT_COUNT - 1).map { it.toUShort(16) }
        val mappedPrefixSegments: List<UShort> = listOf(0u, 0u, 0u, 0u, 0u, 0xffffu)
        if (prefix != mappedPrefixSegments) return null
        val ipv4 = segments[IPV4_MAPPED_IPV6_SEGMENT_COUNT - 1].parseIpv4OrNull() ?: return null
        return ipv4.toMappedIpv6()
    }

    // otherwise, we expect 2 more IPv6 segments
    if (segments.size != IPV6_SEGMENT_COUNT) return null
    if (!segments[IPV6_SEGMENT_COUNT - 2].isIpv6AddressSegment() || !segments[IPV6_SEGMENT_COUNT - 1].isIpv6AddressSegment()) return null

    val parsedSegments = segments.map { it.toUShort(16) }
    return IpV6Addr(
        parsedSegments[0],
        parsedSegments[1],
        parsedSegments[2],
        parsedSegments[3],
        parsedSegments[4],
        parsedSegments[5],
        parsedSegments[6],
        parsedSegments[7],
        zoneId,
    )
}

private fun String.isIpv6AddressSegment(): Boolean = length in 1..4 && all(Char::isHexDigit)

internal fun String.isIpv6ZoneId(): Boolean = isNotEmpty() && '%' !in this

private fun String.isValidPercentEncoded(): Boolean {
    forEachIndexed { index, char ->
        when (char) {
            in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_', '.', '~' -> return@forEachIndexed
            '%' -> {
                if (index > length - 2) return false
                if (!this[index+1].isHexDigit() || !this[index+2].isHexDigit()) return false
            }
            else -> return false
        }
    }
    return true
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
