/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.util

import kotlin.random.Random

/**
 * A KMP-compatible implementation of UUID, necessary because no cross-platform implementation exists yet.
 */
public data class Uuid(val high: Long, val low: Long) {
    public companion object {
        private val nibbleChars = "0123456789abcdef".toCharArray()
        private val random = Random

        private val v4Mask = 0x00000000_0000_f000U.toLong()
        private val v4Set = 0x00000000_0000_4000U.toLong()

        private val type2Mask = 0xc000_000000000000U.toLong()
        private val type2Set = 0x8000_000000000000U.toLong()

        /**
         * Generates a random [Uuid], specifically a
         * [UUID v4](https://en.wikipedia.org/wiki/Universally_unique_identifier#Version_4_.28random.29).
         * UUIDs are not generated with a cryptographically-strong random number generator.
         */
        @WeakRng
        public fun random(): Uuid {
            val high = random.nextLong() and v4Mask.inv() or v4Set
            val low = random.nextLong() and type2Mask.inv() or type2Set
            return Uuid(high, low)
        }

        /**
         * Generates a string representation of a UUID in the form of `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`, where `x`
         * is a hexadecimal digit from 0-f.
         */
        private fun toString(high: Long, low: Long): String {
            val chars = CharArray(36) // 32 hex digits plus 4 hyphens

            writeDigits(high, 0, chars, 0, 4)
            chars[8] = '-'
            writeDigits(high, 4, chars, 9, 2)
            chars[13] = '-'
            writeDigits(high, 6, chars, 14, 2)
            chars[18] = '-'
            writeDigits(low, 0, chars, 19, 2)
            chars[23] = '-'
            writeDigits(low, 2, chars, 24, 6)

            return chars.concatToString()
        }

        /**
         * Write hexadecimal digits to a character array from a source [Long].
         * @param src The source bits as a [Long].
         * @param srcOffset The offset (in bytes, from the left) within [src]. This offset should be between 0 and 8
         * exclusive (since that's how many bytes are in a [Long]).
         * @param dest The character array to receive the digits.
         * @param destOffset The offset (in characters) within [dest]. This offset should generally be double the source
         * offset (because each byte of source becomes two hex digits) plus however many interceding hyphens have been
         * added to the array.
         * @param length The length (in bytes) to write. (Twice this number of characters will be written.)
         */
        private fun writeDigits(src: Long, srcOffset: Int, dest: CharArray, destOffset: Int, length: Int) {
            var shiftBits = 64 - srcOffset * 8
            var destIndex = destOffset

            repeat(length * 2) {
                shiftBits -= 4
                val nibble = src shr shiftBits and 0xf
                dest[destIndex++] = nibbleChars[nibble.toInt()]
            }
        }
    }

    private val stringRep = toString(high, low)

    override fun toString(): String = stringRep

    @RequiresOptIn("This API doesn't use cryptographically-strong random number generation.")
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.FUNCTION)
    public annotation class WeakRng
}
