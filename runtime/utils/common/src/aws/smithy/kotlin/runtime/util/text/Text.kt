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
fun String.urlEncodeComponent(
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
            else -> sb.append(cbyte.percentEncode())
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
val VALID_PCHAR_DELIMS = setOf(
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
fun String.encodeUrlPath() = encodeUrlPath(VALID_PCHAR_DELIMS, checkPercentEncoded = true)

/**
 * Encode a string that represents a raw URL path component. Everything EXCEPT alphanumeric characters
 * and all delimiters in the [validDelimiters] set will be percent encoded.
 *
 * @param validDelimiters the set of allowed delimiters that need not be percent-encoded
 * @param checkPercentEncoded flag indicating if the encoding process should check for already percent-encoded
 * characters and pass them through as is or not.
 */
@InternalApi
fun String.encodeUrlPath(validDelimiters: Set<Char>, checkPercentEncoded: Boolean): String {
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
            else -> sb.append(cbyte.percentEncode())
        }
        i++
    }

    return sb.toString()
}

private const val upperHex: String = "0123456789ABCDEF"
private val upperHexSet = upperHex.toSet()

// $2.1 Percent-Encoding
fun Byte.percentEncode(): String = buildString(3) {
    val code = toInt() and 0xff
    append('%')
    append(upperHex[code shr 4])
    append(upperHex[code and 0x0f])
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

private val percentEncodedParam = """%([A-Fa-f0-9]{2})""".toRegex()

/**
 * Decode a URL's query string, resolving percent-encoding (e.g., "%3B" → ";").
 */
@InternalApi
public fun String.urlDecodeComponent(): String = this
    .replace('+', ' ')
    .replace(percentEncodedParam) { it.groupValues[1].toInt(radix = 16).toChar().toString() }
