/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util.text

import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * URL encode a string component according to
 * https://tools.ietf.org/html/rfc3986#section-2
 */
@InternalApi
public fun String.urlEncodeComponent(
    formUrlEncode: Boolean = false
): String {
    val sb = StringBuilder(this.length)
    val data = this.encodeToByteArray()
    for (cbyte in data) {
        val chr = cbyte.toInt().toChar()
        when (chr) {
            ' ' -> if (formUrlEncode) sb.append("+") else sb.append("%20")
            // $2.3 Unreserved characters
            in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_', '.', '~' -> sb.append(chr)
            else -> cbyte.percentEncodeTo(sb)
        }
    }

    return sb.toString()
}

/**
 * The set of reserved delimiters from section-2.2 allowed as a path character that do not require
 * percent encoding.
 * See 'pchar': https://tools.ietf.org/html/rfc3986#section-3.3
 */
@InternalApi
public val VALID_PCHAR_DELIMS: Set<Char> = setOf(
    '/',
    ':', '@',
    // sub-delims from section-2.2
    '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
    // unreserved section-2.3
    '-', '.', '_', '~'
)

// test if a string encoded to a ByteArray is already percent encoded starting at index [i]
private fun isPercentEncodedAt(d: ByteArray, i: Int): Boolean =
    i + 2 < d.size &&
        d[i].toInt().toChar() == '%' &&
        i + 2 < d.size &&
        d[i + 1].toInt().toChar().uppercaseChar() in upperHexSet &&
        d[i + 2].toInt().toChar().uppercaseChar() in upperHexSet

/**
 * Encode a string that represents a *raw* URL path according to
 * https://tools.ietf.org/html/rfc3986#section-3.3
 */
@InternalApi
public fun String.encodeUrlPath(): String = encodeUrlPath(VALID_PCHAR_DELIMS, checkPercentEncoded = true)

/**
 * Encode a string that represents a raw URL path component. Everything EXCEPT alphanumeric characters
 * and all delimiters in the [validDelimiters] set will be percent encoded.
 *
 * @param validDelimiters the set of allowed delimiters that need not be percent-encoded
 * @param checkPercentEncoded flag indicating if the encoding process should check for already percent-encoded
 * characters and pass them through as is or not.
 */
@InternalApi
public fun String.encodeUrlPath(validDelimiters: Set<Char>, checkPercentEncoded: Boolean): String {
    val sb = StringBuilder(this.length)
    val data = this.encodeToByteArray()

    var i = 0
    while (i < data.size) {
        // 3.3 pchar: pct-encoded
        if (checkPercentEncoded && isPercentEncodedAt(data, i)) {
            sb.append(data[i++].toInt().toChar())
            sb.append(data[i++].toInt().toChar())
            sb.append(data[i++].toInt().toChar())
            continue
        }

        val cbyte = data[i]
        when (val chr = cbyte.toInt().toChar()) {
            // unreserved
            in 'a'..'z', in 'A'..'Z', in '0'..'9', in validDelimiters -> sb.append(chr)
            else -> cbyte.percentEncodeTo(sb)
        }
        i++
    }

    return sb.toString()
}

/**
 * Normalizes the segments of a URL path according to the following rules:
 * * The returned path always begins with `/` (e.g., `a/b/c` → `/a/b/c`)
 * * The returned path ends with `/` if the input path also does
 * * Empty segments are discarded (e.g., `/a//b` → `/a/b`)
 * * Segments of `.` are discarded (e.g., `/a/./b` → `/a/b`)
 * * Segments of `..` are used to discard ancestor paths (e.g., `/a/b/../c` → `/a/c`)
 * * All other segments are unmodified
 */
@InternalApi
public fun String.normalizePathSegments(segmentTransform: ((String) -> String)?): String {
    val segments = split("/").filter(String::isNotEmpty)
    var skip = 0
    val normalizedSegments = buildList {
        segments.asReversed().forEach {
            when {
                it == "." -> { } // Ignore
                it == ".." -> skip++
                skip > 0 -> skip--
                else -> add(it)
            }
        }
    }.asReversed()
    require(skip == 0) { "Found too many `..` instances for path segment count" }

    return normalizedSegments.joinToString(
        separator = "/",
        prefix = "/",
        postfix = if (normalizedSegments.isNotEmpty() && endsWith("/")) "/" else "",
        transform = segmentTransform,
    )
}

@InternalApi
public fun String.transformPathSegments(segmentTransform: ((String) -> String)?): String =
    split("/").joinToString(separator = "/", transform = segmentTransform)

private const val upperHex: String = "0123456789ABCDEF"
private val upperHexSet = upperHex.toSet()

// $2.1 Percent-Encoding
@InternalApi
public fun Byte.percentEncodeTo(out: Appendable) {
    val code = toInt() and 0xff
    out.append('%')
    out.append(upperHex[code shr 4])
    out.append(upperHex[code and 0x0f])
}

/**
 * Split a (decoded) query string "foo=baz&bar=quux" into it's component parts
 */
@InternalApi
public fun String.splitAsQueryString(): Map<String, List<String>> {
    val entries = mutableMapOf<String, MutableList<String>>()
    split("&")
        .forEach { pair ->
            val parts = pair.split("=", limit = 2)
            val key = parts[0]
            val value = when (parts.size) {
                1 -> ""
                2 -> parts[1]
                else -> throw IllegalArgumentException("invalid query string: $parts")
            }
            if (entries.containsKey(key)) {
                entries[key]!!.add(value)
            } else {
                entries[key] = mutableListOf(value)
            }
        }
    return entries
}

/**
 * Decode a URL's query string, resolving percent-encoding (e.g., "%3B" → ";").
 */
@InternalApi
public fun String.urlDecodeComponent(formUrlDecode: Boolean = false): String {
    val orig = this
    return buildString(orig.length) {
        var byteBuffer: ByteArray? = null // Do not initialize unless needed
        var i = 0
        var c: Char
        while (i < orig.length) {
            c = orig[i]
            when {
                c == '+' && formUrlDecode -> {
                    append(' ')
                    i++
                }

                c == '%' -> {
                    if (byteBuffer == null) {
                        byteBuffer = ByteArray((orig.length - i) / 3) // Max remaining percent-encoded bytes
                    }

                    var byteCount = 0
                    while ((i + 2) < orig.length && c == '%') {
                        val byte = orig.substring(i + 1, i + 3).toIntOrNull(radix = 16)?.toByte() ?: break
                        byteBuffer[byteCount++] = byte

                        i += 3
                        if (i < orig.length) c = orig[i]
                    }

                    append(byteBuffer.decodeToString(endIndex = byteCount))

                    if (i != orig.length && c == '%') {
                        append(c)
                        i++
                    }
                }

                else -> {
                    append(c)
                    i++
                }
            }
        }
    }
}

@InternalApi
public fun String.urlReencodeComponent(formUrlDecode: Boolean = false, formUrlEncode: Boolean = false): String =
    urlDecodeComponent(formUrlDecode).urlEncodeComponent(formUrlEncode)

@InternalApi
public fun String.ensureSuffix(suffix: String): String = if (endsWith(suffix)) this else plus(suffix)
