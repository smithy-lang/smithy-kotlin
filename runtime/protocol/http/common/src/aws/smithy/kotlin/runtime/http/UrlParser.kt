/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.isIpv6
import aws.smithy.kotlin.runtime.util.text.splitAsQueryString
import aws.smithy.kotlin.runtime.util.text.urlDecodeComponent

internal fun urlParseImpl(url: String): Url =
    UrlBuilder {
        var next = url
            .captureUntilAndSkip("://") {
                scheme = Protocol.parse(it)
            }
            .captureUntilAndSkip("@") {
                userInfo = UserInfo(it)
            }

        next = next.capture(0 until next.firstIndexOrEnd("/", "?", "#")) {
            val (h, p) = it.splitHostPort()
            host = h
            port = p ?: scheme.defaultPort
        }

        if (next.startsWith("/")) {
            next = next.capture(1 until next.firstIndexOrEnd("?", "#")) {
                path = "/${it.urlDecodeComponent()}"
            }
        }

        if (next.startsWith("?")) {
            next = next.capture(1 until next.firstIndexOrEnd("#")) {
                it.splitAsQueryString().entries.forEach { (k, v) ->
                    parameters.appendAll(k.urlDecodeComponent(), v.map(String::urlDecodeComponent))
                }
            }
        }

        if (next.startsWith('#') && next.length > 1) {
            fragment = next.substring(1).urlDecodeComponent()
        }
    }

private fun String.firstIndexOrEnd(vararg substring: String): Int {
    val indices = substring
        .map { indexOf(it) }
        .filter { it != -1 }
    if (indices.isEmpty()) return length

    return minOf(indices.min(), length)
}

private fun String.captureUntilAndSkip(substring: String, block: (String) -> Unit): String {
    val substringIndex = indexOf(substring)
    if (substringIndex == -1) {
        return this
    }

    val slice = substring(0 until substringIndex)
    block(slice)
    return substring(substringIndex + substring.length)
}

private fun String.capture(range: IntRange, block: (String) -> Unit): String {
    val slice = substring(range)
    if (slice.isNotEmpty()) {
        block(slice)
    }
    return substring(range.last + 1)
}

/**
 * Parses a `host[:port]` pair. IPv6 hostnames MUST be enclosed in square brackets (`[]`).
 */
internal fun String.splitHostPort(): Pair<Host, Int?> {
    val lBracketIndex = indexOf('[')
    val rBracketIndex = indexOf(']')
    val lastColonIndex = lastIndexOf(":")
    val hostEndIndex = when {
        rBracketIndex != -1 -> rBracketIndex + 1
        lastColonIndex != -1 -> lastColonIndex
        else -> length
    }

    require(lBracketIndex == -1 && rBracketIndex == -1 || lBracketIndex < rBracketIndex) { "unmatched [ or ]" }
    require(lBracketIndex <= 0) { "unexpected characters before [" }
    require(rBracketIndex == -1 || rBracketIndex == hostEndIndex - 1) { "unexpected characters after ]" }

    val host = if (lBracketIndex != -1) {
        substring(lBracketIndex + 1 until rBracketIndex)
    } else {
        substring(0 until hostEndIndex)
    }

    val decodedHost = host.urlDecodeComponent()
    if (lBracketIndex != -1 && rBracketIndex != -1 && !decodedHost.isIpv6()) {
        throw IllegalArgumentException("non-ipv6 host was enclosed in []-brackets")
    }

    return Pair(
        Host.parse(decodedHost),
        if (hostEndIndex != -1 && hostEndIndex != length) substring(hostEndIndex + 1).toInt() else null,
    )
}
