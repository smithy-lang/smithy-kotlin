/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

private val hexChars = "0123456789abcdef".toCharArray()

/**
 * Encode [bytes] as lowercase HEX string
 */
public fun encodeHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    for (i in bytes.indices) {
        val byte = bytes[i].toInt() and 0xff
        append(hexChars[byte shr 4])
        append(hexChars[byte and 0x0f])
    }
}

public fun ByteArray.encodeToHex(): String = encodeHex(this)

/**
 * Decode bytes from HEX string. There should be no spaces or `0x` prefixes
 */
public fun decodeHex(s: String): ByteArray {
    val result = ByteArray((s.length + 1) / 2)
    var start = 0
    var writeIdx = 0

    // if the buffer isn't even, prepend 0
    if (s.length % 2 == 1) {
        result[writeIdx++] = s[0].digitToInt(16).toByte()
        start = writeIdx
    }

    for (i in start until s.length step 2) {
        val high = s[i].digitToInt(16) shl 4
        val low = s[i + 1].digitToInt(16)
        result[writeIdx++] = (high or low).toByte()
    }
    return result
}

public fun String.decodeHexBytes(): ByteArray = decodeHex(this)
