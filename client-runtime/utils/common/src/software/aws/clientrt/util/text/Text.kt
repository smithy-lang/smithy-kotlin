/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.util.text

import software.aws.clientrt.util.InternalApi

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
    for (i in data.indices) {
        val cbyte = data[i]
        val chr = cbyte.toChar()
        when (chr) {
            ' ' -> if (formUrlEncode) sb.append("+") else sb.append("%20")
            // $2.3 Unreserved characters
            in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_', '.', '~' -> sb.append(chr)
            else -> sb.append(cbyte.percentEncode())
        }
    }

    return sb.toString()
}

// from 'pchar' https://tools.ietf.org/html/rfc3986#section-3.3
//
// note `:` is a valid pchar by the RFC but we are going to percent encode
// it anyway (by not including it in this list). tl;dr is that implementations
// vary and many "over percent-encode" path components since they don't distinguish
// from other uri components. There _shouldn't_ be any harm in over percent-encoding as
// it'll just be decoded on the other side like any other percent-encoded char.
//
// this can affect things like signing, see: smithy-kotlin/issues/118
// if there are other deviations in the future and you find yourself yet again editing
// this definition, STOP and consider just making it configurable instead and returning to sanity
private val VALID_PATH_PART = listOf(
    '@',
    // sub-delims from section-2.2
    '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
    // unreserved section-2.3
    '-', '.', '_', '~'
)

// test if a string encoded to a ByteArray is already percent encoded starting at index [i]
private fun isPercentEncodedAt(d: ByteArray, i: Int): Boolean {
    if (i >= d.size) return false
    return d[i].toChar() == '%' &&
        i + 2 < d.size &&
        d[i + 1].toChar().toUpperCase() in upperHex &&
        d[i + 2].toChar().toUpperCase() in upperHex
}

/**
 * Encode a string that represents a raw URL path
 * https://tools.ietf.org/html/rfc3986#section-3.3
 */
@InternalApi
fun String.encodeUrlPath(): String {
    val sb = StringBuilder(this.length)
    val data = this.encodeToByteArray()

    var i = 0
    while (i < data.size) {
        // 3.3 pchar: pct-encoded
        if (isPercentEncodedAt(data, i)) {
            sb.append(data[i++].toChar())
            sb.append(data[i++].toChar())
            sb.append(data[i++].toChar())
            continue
        }

        val cbyte = data[i]
        when (val chr = cbyte.toChar()) {
            // unreserved
            in 'a'..'z', in 'A'..'Z', in '0'..'9', '/', in VALID_PATH_PART -> sb.append(chr)
            else -> sb.append(cbyte.percentEncode())
        }
        i++
    }

    return sb.toString()
}

private const val upperHex: String = "0123456789ABCDEF"

// $2.1 Percent-Encoding
private fun Byte.percentEncode(): String = buildString(3) {
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
            val parts = pair.split("=")
            val key = parts[0]
            val value = if (parts.size > 1) parts[1] else ""
            if (entries.containsKey(key)) {
                entries[key]!!.add(value)
            } else {
                entries[key] = mutableListOf(value)
            }
        }
    return entries
}
