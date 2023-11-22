/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.text

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
@Suppress("NOTHING_TO_INLINE")
public inline fun byteCountUtf8(start: Byte): Int {
    val x = start.toUInt()
    return when {
        x <= 0x7fu -> 1 // 0xxx xxxx one byte
        x and 0xe0u == 0xc0u -> 2 // 110x xxxx two bytes
        x and 0xf0u == 0xe0u -> 3 // 1110 xxxx three bytes
        x and 0xf8u == 0xf0u -> 4 // 1111 0xxx 4 bytes
        else -> throw IllegalStateException("$start is not a valid UTF-8 start sequence")
    }
}

/**
 * The minimum value of a supplementary code point, `\u0x10000`.
 */
private const val SUPPLEMENTARY_PLANE_LOW: Int = 0x010000

/**
 * Maximum value of a Unicode code point
 */
private const val MAX_CODEPOINT: Int = 0X10FFFF

/**
 * Checks to see if a codepoint is in the supplementary plane or not (surrogate pair)
 */
@InternalApi
public fun Char.Companion.isSupplementaryCodePoint(codePoint: Int): Boolean = codePoint in SUPPLEMENTARY_PLANE_LOW..MAX_CODEPOINT

/**
 * Converts the [codePoint] to a char array. If the codepoint is in the supplementary plane then it will
 * return an array with the high surrogate and low surrogate at indexes 0 and 1. Otherwise it will return a char
 * array with a single character.
 */
@InternalApi
public fun Char.Companion.codePointToChars(codePoint: Int): CharArray = when (codePoint) {
    in 0 until SUPPLEMENTARY_PLANE_LOW -> charArrayOf(codePoint.toChar())
    in SUPPLEMENTARY_PLANE_LOW..MAX_CODEPOINT -> {
        val low = MIN_LOW_SURROGATE.code + ((codePoint - 0x10000) and 0x3FF)
        val high = MIN_HIGH_SURROGATE.code + (((codePoint - 0x10000) ushr 10) and 0x3FF)
        charArrayOf(high.toChar(), low.toChar())
    }
    else -> throw IllegalArgumentException("invalid codepoint $codePoint")
}
