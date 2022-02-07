/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.util

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Attempt to split an HTTP header [value] by commas and returns the resulting list.
 * This parsing implements [RFC-7230's](https://datatracker.ietf.org/doc/html/rfc7230#section-3.2.6)
 * specification for header values.
 */
@InternalApi
fun splitHeaderListValues(value: String): List<String> {
    // value.split(",").map { it.trim() }
    val results = mutableListOf<String>()
    var currIdx = 0
    while (currIdx < value.length) {
        val next = when (value[currIdx]) {
            // skip ws
            ' ', '\t' -> {
                currIdx++
                continue
            }
            '\"' -> value.readNextQuoted(currIdx)
            else -> value.readNextUnquoted(currIdx)
        }
        currIdx = next.first
        results.add(next.second)
    }

    return results
}

private fun String.readNextQuoted(startIdx: Int, delim: Char = ','): Pair<Int, String> {
    // startIdx is start of the quoted value, there must be at least an ending quotation mark
    check(startIdx + 1 < length) { "unbalanced quoted header value" }

    // find first non-escaped quote or end of string
    var endIdx = startIdx + 1
    while (endIdx < length) {
        when (this[endIdx]) {
            '\\' -> endIdx++ // skip escaped chars
            '"' -> break
        }
        endIdx++
    }

    val next = substring(startIdx + 1, endIdx)

    // consume trailing quote
    if (endIdx < length && this[endIdx] == '"') endIdx++

    // consume delim
    if (endIdx < length && this[endIdx] == delim) endIdx++

    val unescaped = next.replace("\\\"", "\"")
        .replace("\\\\", "\\")

    return Pair(endIdx, unescaped)
}

private fun String.readNextUnquoted(startIdx: Int, delim: Char = ','): Pair<Int, String> {
    check(startIdx < this.length)
    var endIdx = startIdx
    while (endIdx < length && this[endIdx] != delim) endIdx++

    val next = substring(startIdx, endIdx)
    if (endIdx < length && this[endIdx] == delim) endIdx++

    return Pair(endIdx, next.trim())
}

/**
 * Attempt to split an HTTP header [value] as if it contains a list of HTTP-Date timestamp
 * values separated by commas. The split is aware of the HTTP-Date format and will skip
 * a comma within the timestamp value.
 */
@InternalApi
fun splitHttpDateHeaderListValues(value: String): List<String> {
    val n = value.count { it == ',' }
    if (n <= 1) {
        return listOf(value)
    } else if (n % 2 == 0) {
        throw ClientException("invalid timestamp HttpDate header comma separations: `$value`")
    }

    var cnt = 0
    val splits = mutableListOf<String>()
    var startIdx = 0

    for (i in value.indices) {
        if (value[i] == ',') cnt++

        // split on every other ','
        if (cnt > 1) {
            splits.add(value.substring(startIdx, i).trim())
            startIdx = i + 1
            cnt = 0
        }
    }

    if (startIdx < value.length) {
        splits.add(value.substring(startIdx).trim())
    }

    return splits
}

// chars in an HTTP header value that require quotations
private const val QUOTABLE_HEADER_VALUE_CHARS = "\",()"

/**
 * Conditionally quotes and escapes a header value if the header value contains a comma or quote
 */
@InternalApi
fun quoteHeaderValue(value: String): String =
    if (value.trim().length != value.length || QUOTABLE_HEADER_VALUE_CHARS.any { value.contains(it) }) {
        val formatted = value.replace("\\", "\\\\").replace("\"", "\\\"")
        "\"$formatted\""
    } else {
        value
    }
