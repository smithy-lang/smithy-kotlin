/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.util.crypto

import kotlin.math.abs
import kotlin.math.sin

private const val BLOCK_SIZE = 64
private const val PADDING_START_BYTE = 0x80.toByte()

class Md5 : Digest {
    private var a = INIT_A
    private var b = INIT_B
    private var c = INIT_C
    private var d = INIT_D

    private var length = 0

    /**
     * Holds the incomplete portion of a block that's not ready for digesting yet.
     */
    private val remainder = ByteArray(BLOCK_SIZE)

    /**
     * The number of bytes in the [remainder] that are used (0-63).
     */
    private var remainderSize = 0

    private val buffer = IntArray(16) // Optimization to reduce allocations in digestBlock()

    override fun append(input: ByteArray) {
        if (input.size < BLOCK_SIZE - remainderSize) { // We're not appending enough for a full block incl remainder
            input.copyInto(remainder, destinationOffset = remainderSize)
            remainderSize += input.size
            length += input.size
            return
        }

        var inputOffset = 0
        if (remainderSize > 0) { // We got remainders left over so concatenate bytes from the input and digest
            input.copyInto(remainder, destinationOffset = remainderSize, endIndex = BLOCK_SIZE - remainderSize)
            digestBlock(remainder, 0)
            inputOffset = BLOCK_SIZE - remainderSize
        }

        while (inputOffset <= input.size - BLOCK_SIZE) {
            digestBlock(input, inputOffset)
            inputOffset += BLOCK_SIZE
        }

        remainderSize = input.size - inputOffset
        if (remainderSize > 0) input.copyInto(remainder, startIndex = inputOffset)

        length += input.size
    }

    override fun compute(): ByteArray = copy().run {
        val messageLenBytes = length
        val numBlocks = ((messageLenBytes + 8) ushr 6) + 1
        val totalLen = numBlocks shl 6
        val paddingBytes = ByteArray(totalLen - messageLenBytes)
        paddingBytes[0] = PADDING_START_BYTE
        var messageLenBits = messageLenBytes shl 3

        for (i in 0..7) {
            paddingBytes[paddingBytes.size - 8 + i] = messageLenBits.toByte()
            messageLenBits = messageLenBits ushr 8
        }

        append(paddingBytes)

        val md5 = ByteArray(16)
        for (i in 0..3) {
            var n = when (i) {
                0 -> a
                1 -> b
                2 -> c
                else -> d
            }
            for (j in 0..3) {
                md5[i * 4 + j] = n.toByte()
                n = n ushr 8
            }
        }

        md5
    }

    private fun copy(): Md5 {
        val copy = Md5()
        copy.a = a
        copy.b = b
        copy.c = c
        copy.d = d
        copy.length = length
        remainder.copyInto(copy.remainder)
        copy.remainderSize = remainderSize
        return copy
    }

    /**
     * Digests a complete block.
     */
    private fun digestBlock(block: ByteArray, startByteIndex: Int) {
        for (j in 0 until BLOCK_SIZE) {
            val byte = block[startByteIndex + j]
            val i = j ushr 2
            buffer[i] = (byte.toInt() shl 24) or (buffer[i] ushr 8)
        }

        val origA = a
        val origB = b
        val origC = c
        val origD = d

        for (j in 0 until BLOCK_SIZE) {
            val div16 = j ushr 4
            var f = 0
            var bufferIndex = j
            when (div16) {
                0 -> {
                    f = (b and c) or (b.inv() and d)
                }

                1 -> {
                    f = (b and d) or (c and d.inv())
                    bufferIndex = (bufferIndex * 5 + 1) and 0x0F
                }

                2 -> {
                    f = b xor c xor d
                    bufferIndex = (bufferIndex * 3 + 5) and 0x0F
                }

                3 -> {
                    f = c xor (b or d.inv())
                    bufferIndex = (bufferIndex * 7) and 0x0F
                }
            }

            val hashBase = a + f + buffer[bufferIndex] + SINE_TABLE[j]
            val shiftAmount = SHIFT_AMOUNTS[(div16 shl 2) or (j and 3)]
            val temp = b + (hashBase rl shiftAmount)
            a = d
            d = c
            c = b
            b = temp
        }

        a += origA
        b += origB
        c += origC
        d += origD
    }

    override fun reset() {
        a = INIT_A
        b = INIT_B
        c = INIT_C
        d = INIT_D
        length = 0
        remainderSize = 0
    }
}

private infix fun Int.rl(bits: Int): Int = (this shl bits) or (this ushr (32 - bits))

private const val INIT_A: Int = 0x67452301
private const val INIT_B: Int = 0xEFCDAB89L.toInt()
private const val INIT_C: Int = 0x98BADCFEL.toInt()
private const val INIT_D: Int = 0x10325476

private val SHIFT_AMOUNTS = intArrayOf(
    7,
    12,
    17,
    22,
    5,
    9,
    14,
    20,
    4,
    11,
    16,
    23,
    6,
    10,
    15,
    21,
)

private val SINE_TABLE = (1..BLOCK_SIZE).map {
    (0x100000000L * abs(sin(it.toDouble()))).toLong().toInt()
}.toIntArray()
