/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Validates a hostname per [RFC 1123](https://www.ietf.org/rfc/rfc1123.txt).
 */
@InternalApi
public fun String.isValidHostname(): Boolean =
    length in 1..63 && this[0].isLetterOrDigit() && drop(1).all { it.isLetterOrDigit() || it == '-' }

@InternalApi
public fun String.isIpv4(): Boolean {
    val segments = split('.')
    return segments.size == 4 && segments.all { (it.toIntOrNull() ?: -1) in 0..255 }
}

/**
 * Validates a string as an IPv6 address according to [RFC 4291 §2.2](https://www.rfc-editor.org/rfc/rfc4291.html#section-2.2).
 * Does NOT validate for [address prefixes (§2.3)](https://www.rfc-editor.org/rfc/rfc4291.html#section-2.3).
 *
 * The address MAY include a scope ID which is validated per [RFC 4007](https://www.rfc-editor.org/rfc/rfc4007#section-11.2).
 */
@InternalApi
public fun String.isIpv6(): Boolean {
    val components = split('%')

    if (components.size > 2) return false
    if (components.size == 2 && !components[1].isIpv6ScopeId()) return false
    return components[0].isIpv6Address()
}

private const val ipv6StandardSegmentCount = 8
private const val ipv6DualSegmentCount = 7

private fun String.getIpv6AddressSegments(): List<String>? {
    val explicitSegmentGroups = split("::")
    if (explicitSegmentGroups.size > 2) {
        return null
    }
    if (explicitSegmentGroups.size == 1) { // this would mean the address is explicit as-written
        return explicitSegmentGroups[0].split(":")
    }

    val leftSegments = if (explicitSegmentGroups[0] == "") listOf() else explicitSegmentGroups[0].split(':')
    val rightSegments = if (explicitSegmentGroups[1] == "") listOf() else explicitSegmentGroups[1].split(':')

    val totalSegmentCount = if (rightSegments.lastOrNull()?.contains('.') == true) ipv6DualSegmentCount else ipv6StandardSegmentCount
    val implicitSegmentCount = totalSegmentCount - leftSegments.size - rightSegments.size

    return buildList {
        addAll(leftSegments)
        repeat(implicitSegmentCount) { add("0") }
        addAll(rightSegments)
    }
}

private fun String.isIpv6Address(): Boolean {
    val segments = getIpv6AddressSegments() ?: return false
    if (segments.size < ipv6DualSegmentCount) return false

    // the "common" segments MUST be valid IPv6
    for (i in 0 until ipv6DualSegmentCount - 1) {
        if (!segments[i].isIpv6AddressSegment()) return false
    }

    // if this is a dual address, the last segment MUST be IPv4
    if (segments.size == ipv6DualSegmentCount) return segments[ipv6DualSegmentCount - 1].isIpv4()

    // otherwise, we expect 2 more IPv6 segments
    if (segments.size != ipv6StandardSegmentCount) return false

    return segments[ipv6StandardSegmentCount - 2].isIpv6AddressSegment() && segments[ipv6StandardSegmentCount - 1].isIpv6AddressSegment()
}

private fun String.isIpv6AddressSegment(): Boolean = length in 1..4 && all(Char::isHexDigit)

private fun String.isIpv6ScopeId(): Boolean = isNotEmpty() && '%' !in this

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
