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
package software.aws.clientrt.util

private const val BASE64_ENCODE_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
private const val BASE64_PAD_SENTINEL = 0xff
private const val BASE64_PAD = '='

// ascii ordinal value to position in encode table
// invalid characters are encoded as -1, padding char '=' is encoded as PAD_SENTINEL
private val BASE64_DECODE_TABLE = IntArray(256) {
    if (it == 61) {
        BASE64_PAD_SENTINEL
    } else {
        BASE64_ENCODE_TABLE.indexOf(it.toChar())
    }
}

// returns the padded base64 encoded size of [length]
private fun base64EncodedLen(srcLen: Int): Int {
    // 4n/3 is the un-padded size
    return 4 * ((srcLen + 2) / 3)
}

private fun base64DecodedLen(encoded: ByteArray): Int {
    if (encoded.isEmpty()) return 0

    val len = encoded.size

    // multiple of 4, last 2 bits must be 00
    if (len and 0x03 != 0) {
        throw IllegalArgumentException("invalid base64 string of length $len; not a multiple of 4")
    }

    // figure out if the encoded string ends with 0, 1, or 2 bytes of padding ('=')
    var padding = 0
    if (len >= 2 && encoded[len - 1] == BASE64_PAD.toByte() && encoded[len - 2] == BASE64_PAD.toByte()) {
        padding = 2
    } else if (encoded[len - 1] == BASE64_PAD.toByte()) {
        padding = 1
    }

    return ((len * 3) / 4) - padding
}

/**
 * Encode [String] in base64 format and UTF-8 character encoding.
 */
@OptIn(ExperimentalStdlibApi::class)
fun String.encodeBase64(): String = encodeToByteArray().encodeBase64().decodeToString()

/**
 * Encode [ByteArray] in base64 format and UTF-8 character encoding.
 */
fun ByteArray.encodeBase64(): ByteArray {
    val output = ByteArray(base64EncodedLen(size))
    val remainderCnt = size % 3
    val blockCnt = (size + 2) / 3
    var writeIdx = 0

    for (i in indices step 3) {
        // block: 00000000 xxxxxxxx yyyyyyyy zzzzzzzz
        val block: Int = (getOrZero(i, 0xff) shl 16) or (getOrZero(i + 1, 0xff) shl 8) or getOrZero(i + 2, 0xff)

        // split block: xxxxxx xxyyyy yyyyzz zzzzzz
        output[writeIdx++] = ((block shr 18) and 0x3F).toBase64()
        output[writeIdx++] = ((block shr 12) and 0x3F).toBase64()
        output[writeIdx++] = ((block shr 6) and 0x3F).toBase64()
        output[writeIdx++] = (block and 0x3F).toBase64()
    }

    // padding - always 0, 1, or 2
    // each block is 4 chars, we need (3-remainderCnt) pad chars
    if (remainderCnt > 0) {
        output[blockCnt * 4 - 1] = BASE64_PAD.toByte()
        if (remainderCnt == 1) {
            output[blockCnt * 4 - 2] = BASE64_PAD.toByte()
        }
    }

    return output
}

/**
 * Decode [String] from base64 format
 */
@OptIn(ExperimentalStdlibApi::class)
fun String.decodeBase64(): String = encodeToByteArray().decodeBase64().decodeToString()

/**
 * Decode [ByteArray] from base64 format
 */
fun ByteArray.decodeBase64(): ByteArray {
    val encoded = this
    val decodedLen = base64DecodedLen(encoded)
    val decoded = ByteArray(decodedLen)
    val blockCnt = size / 4
    var readIdx = 0
    var writeIdx = 0

    for (i in 0 until blockCnt - 1) {
        // encoded: xxxxxx xxyyyy yyyyzz zzzzzz
        val block = ((encoded[readIdx++].fromBase64() shl 18) or
                (encoded[readIdx++].fromBase64() shl 12) or
                (encoded[readIdx++].fromBase64() shl 6) or
                (encoded[readIdx++].fromBase64() and 0xff))

        // decoded: xxxxxxxx yyyyyyyy zzzzzzzz
        for (j in 2 downTo 0) {
            decoded[writeIdx++] = ((block shr (j * 8)) and 0xff).toByte()
        }
    }

    // deal with last block where last two bytes *may* be padding
    val bufIdx = (blockCnt - 1) * 3
    if (bufIdx >= 0) {
        val v1 = encoded[readIdx++].fromBase64()
        val v2 = encoded[readIdx++].fromBase64()
        val v3 = encoded[readIdx++].fromBase64()
        val v4 = encoded[readIdx].fromBase64()

        if (v1 == BASE64_PAD_SENTINEL || v2 == BASE64_PAD_SENTINEL) {
            throw IllegalArgumentException("decode base64: invalid padding")
        }

        decoded[writeIdx++] = (v1 shl 2 or (v2 shr 4 and 0x03)).toByte()
        if (v3 != BASE64_PAD_SENTINEL) {
            decoded[writeIdx++] = (v2 shl 4 and 0xF0 or (v3 shr 2 and 0x0F)).toByte()
        }
        if (v4 != BASE64_PAD_SENTINEL) {
            decoded[writeIdx] = (((v3 and 0x03) shl 6) or v4).toByte()
        }
    }

    return decoded
}

private fun Int.toBase64(): Byte = BASE64_ENCODE_TABLE[this].toByte()
private fun ByteArray.getOrZero(index: Int, mask: Int? = null): Int {
    return if (index >= size) {
        0
    } else {
        var tmp = this[index].toInt()
        if (mask != null) {
            tmp = tmp and mask
        }
        tmp
    }
}
private fun Byte.fromBase64(): Int {
    val decoded = BASE64_DECODE_TABLE[this.toInt()]
    if (decoded == -1) throw IllegalArgumentException("decode base64: invalid input byte: $this")
    return decoded
}
