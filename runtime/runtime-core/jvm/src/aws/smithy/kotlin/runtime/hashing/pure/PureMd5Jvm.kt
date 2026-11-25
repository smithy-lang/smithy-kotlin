/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

internal actual val MD5_WORDS_SIZE: Int = 16

/**
 * JVM MD5 block function: rolled round loops with the `K`/`S` tables and the message-word array read into locals once
 * per call. Writing the steps out measured no faster on the JVM.
 */
internal actual fun md5ProcessBlocks(state: IntArray, words: IntArray, input: ByteArray, offset: Int, count: Int) {
    val k = MD5_K
    val s = MD5_S
    val x = words
    var s0 = state[0]
    var s1 = state[1]
    var s2 = state[2]
    var s3 = state[3]

    var off = offset
    repeat(count) {
        for (i in 0 until 16) x[i] = input.getIntLe(off + 4 * i)

        var a = s0
        var b = s1
        var c = s2
        var d = s3

        for (i in 0 until 16) {
            val f = (b and c) or (b.inv() and d)
            val t = d
            d = c
            c = b
            b += (a + f + k[i] + x[i]).rotateLeft(s[i])
            a = t
        }
        for (i in 16 until 32) {
            val f = (d and b) or (d.inv() and c)
            val t = d
            d = c
            c = b
            b += (a + f + k[i] + x[(5 * i + 1) and 15]).rotateLeft(s[i])
            a = t
        }
        for (i in 32 until 48) {
            val f = b xor c xor d
            val t = d
            d = c
            c = b
            b += (a + f + k[i] + x[(3 * i + 5) and 15]).rotateLeft(s[i])
            a = t
        }
        for (i in 48 until 64) {
            val f = c xor (b or d.inv())
            val t = d
            d = c
            c = b
            b += (a + f + k[i] + x[(7 * i) and 15]).rotateLeft(s[i])
            a = t
        }

        s0 += a
        s1 += b
        s2 += c
        s3 += d
        off += 64
    }

    state[0] = s0
    state[1] = s1
    state[2] = s2
    state[3] = s3
}

private val MD5_S = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)

private val MD5_K = longArrayOf(
    0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee, 0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
    0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be, 0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
    0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa, 0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
    0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed, 0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
    0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c, 0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
    0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05, 0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
    0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039, 0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
    0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1, 0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391,
).let { k -> IntArray(k.size) { k[it].toInt() } }
