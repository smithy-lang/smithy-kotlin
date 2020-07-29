/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http.util

/**
 * URL encode a string component according to
 * https://tools.ietf.org/html/rfc3986#section-2
 */
@OptIn(ExperimentalStdlibApi::class)
fun String.urlEncodeComponent(
    formUrlEncode: Boolean = false
): String {
    // TODO - optimize by checking if string even needs escaped and setting the string builder length or returning original string unmodified
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
private val VALID_PATH_PART = listOf(
    ':', '@',
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
@OptIn(ExperimentalStdlibApi::class)
fun String.encodeUrlPath(): String {
    val sb = StringBuilder(this.length)
    val data = this.encodeToByteArray()

    var i = 0
    while (i < data.size) {
        // 3.3 pchar: pct-encoded
        if (isPercentEncodedAt(data, i)) {
            i += 3
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

const val upperHex: String = "0123456789ABCDEF"

// $2.1 Percent-Encoding
private fun Byte.percentEncode(): String = buildString(3) {
    val code = toInt() and 0xff
    append('%')
    append(upperHex[code shr 4])
    append(upperHex[code and 0x0f])
}
