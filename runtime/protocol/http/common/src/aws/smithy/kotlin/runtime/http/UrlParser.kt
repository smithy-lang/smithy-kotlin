/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.util.net.splitHostPort
import aws.smithy.kotlin.runtime.util.text.splitAsQueryString

internal fun String.toUrl(): Url =
    UrlBuilder {
        var next = this@toUrl
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
                path = "/$it"
            }
        }

        if (next.startsWith("?")) {
            next = next.capture(1 until next.firstIndexOrEnd("#")) {
                it.splitAsQueryString().entries.forEach { (k, v) ->
                    parameters.appendAll(k, v)
                }
            }
        }

        if (next.startsWith('#')) {
            fragment = next.substring(1)
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
