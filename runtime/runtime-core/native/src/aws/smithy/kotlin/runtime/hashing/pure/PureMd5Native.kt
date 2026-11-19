/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

internal actual val MD5_WORDS_SIZE: Int = 0

/**
 * Native MD5 block function: all 64 steps written out with literal constants and shifts, the message words in locals
 * x0..x15 and the state variables renamed each step instead of moved, so nothing round-trips through memory.
 */
internal actual fun md5ProcessBlocks(state: IntArray, words: IntArray, input: ByteArray, offset: Int, count: Int) {
    var s0 = state[0]
    var s1 = state[1]
    var s2 = state[2]
    var s3 = state[3]

    var off = offset
    repeat(count) {
        val x0 = input.getIntLe(off)
        val x1 = input.getIntLe(off + 4)
        val x2 = input.getIntLe(off + 8)
        val x3 = input.getIntLe(off + 12)
        val x4 = input.getIntLe(off + 16)
        val x5 = input.getIntLe(off + 20)
        val x6 = input.getIntLe(off + 24)
        val x7 = input.getIntLe(off + 28)
        val x8 = input.getIntLe(off + 32)
        val x9 = input.getIntLe(off + 36)
        val x10 = input.getIntLe(off + 40)
        val x11 = input.getIntLe(off + 44)
        val x12 = input.getIntLe(off + 48)
        val x13 = input.getIntLe(off + 52)
        val x14 = input.getIntLe(off + 56)
        val x15 = input.getIntLe(off + 60)

        var a = s0
        var b = s1
        var c = s2
        var d = s3
        // round 1
        a = b + (a + ((b and c) or (b.inv() and d)) + 0xd76aa478.toInt() + x0).rotateLeft(7)
        d = a + (d + ((a and b) or (a.inv() and c)) + 0xe8c7b756.toInt() + x1).rotateLeft(12)
        c = d + (c + ((d and a) or (d.inv() and b)) + 0x242070db + x2).rotateLeft(17)
        b = c + (b + ((c and d) or (c.inv() and a)) + 0xc1bdceee.toInt() + x3).rotateLeft(22)
        a = b + (a + ((b and c) or (b.inv() and d)) + 0xf57c0faf.toInt() + x4).rotateLeft(7)
        d = a + (d + ((a and b) or (a.inv() and c)) + 0x4787c62a + x5).rotateLeft(12)
        c = d + (c + ((d and a) or (d.inv() and b)) + 0xa8304613.toInt() + x6).rotateLeft(17)
        b = c + (b + ((c and d) or (c.inv() and a)) + 0xfd469501.toInt() + x7).rotateLeft(22)
        a = b + (a + ((b and c) or (b.inv() and d)) + 0x698098d8 + x8).rotateLeft(7)
        d = a + (d + ((a and b) or (a.inv() and c)) + 0x8b44f7af.toInt() + x9).rotateLeft(12)
        c = d + (c + ((d and a) or (d.inv() and b)) + 0xffff5bb1.toInt() + x10).rotateLeft(17)
        b = c + (b + ((c and d) or (c.inv() and a)) + 0x895cd7be.toInt() + x11).rotateLeft(22)
        a = b + (a + ((b and c) or (b.inv() and d)) + 0x6b901122 + x12).rotateLeft(7)
        d = a + (d + ((a and b) or (a.inv() and c)) + 0xfd987193.toInt() + x13).rotateLeft(12)
        c = d + (c + ((d and a) or (d.inv() and b)) + 0xa679438e.toInt() + x14).rotateLeft(17)
        b = c + (b + ((c and d) or (c.inv() and a)) + 0x49b40821 + x15).rotateLeft(22)
        // round 2
        a = b + (a + ((d and b) or (d.inv() and c)) + 0xf61e2562.toInt() + x1).rotateLeft(5)
        d = a + (d + ((c and a) or (c.inv() and b)) + 0xc040b340.toInt() + x6).rotateLeft(9)
        c = d + (c + ((b and d) or (b.inv() and a)) + 0x265e5a51 + x11).rotateLeft(14)
        b = c + (b + ((a and c) or (a.inv() and d)) + 0xe9b6c7aa.toInt() + x0).rotateLeft(20)
        a = b + (a + ((d and b) or (d.inv() and c)) + 0xd62f105d.toInt() + x5).rotateLeft(5)
        d = a + (d + ((c and a) or (c.inv() and b)) + 0x02441453 + x10).rotateLeft(9)
        c = d + (c + ((b and d) or (b.inv() and a)) + 0xd8a1e681.toInt() + x15).rotateLeft(14)
        b = c + (b + ((a and c) or (a.inv() and d)) + 0xe7d3fbc8.toInt() + x4).rotateLeft(20)
        a = b + (a + ((d and b) or (d.inv() and c)) + 0x21e1cde6 + x9).rotateLeft(5)
        d = a + (d + ((c and a) or (c.inv() and b)) + 0xc33707d6.toInt() + x14).rotateLeft(9)
        c = d + (c + ((b and d) or (b.inv() and a)) + 0xf4d50d87.toInt() + x3).rotateLeft(14)
        b = c + (b + ((a and c) or (a.inv() and d)) + 0x455a14ed + x8).rotateLeft(20)
        a = b + (a + ((d and b) or (d.inv() and c)) + 0xa9e3e905.toInt() + x13).rotateLeft(5)
        d = a + (d + ((c and a) or (c.inv() and b)) + 0xfcefa3f8.toInt() + x2).rotateLeft(9)
        c = d + (c + ((b and d) or (b.inv() and a)) + 0x676f02d9 + x7).rotateLeft(14)
        b = c + (b + ((a and c) or (a.inv() and d)) + 0x8d2a4c8a.toInt() + x12).rotateLeft(20)
        // round 3
        a = b + (a + (b xor c xor d) + 0xfffa3942.toInt() + x5).rotateLeft(4)
        d = a + (d + (a xor b xor c) + 0x8771f681.toInt() + x8).rotateLeft(11)
        c = d + (c + (d xor a xor b) + 0x6d9d6122 + x11).rotateLeft(16)
        b = c + (b + (c xor d xor a) + 0xfde5380c.toInt() + x14).rotateLeft(23)
        a = b + (a + (b xor c xor d) + 0xa4beea44.toInt() + x1).rotateLeft(4)
        d = a + (d + (a xor b xor c) + 0x4bdecfa9 + x4).rotateLeft(11)
        c = d + (c + (d xor a xor b) + 0xf6bb4b60.toInt() + x7).rotateLeft(16)
        b = c + (b + (c xor d xor a) + 0xbebfbc70.toInt() + x10).rotateLeft(23)
        a = b + (a + (b xor c xor d) + 0x289b7ec6 + x13).rotateLeft(4)
        d = a + (d + (a xor b xor c) + 0xeaa127fa.toInt() + x0).rotateLeft(11)
        c = d + (c + (d xor a xor b) + 0xd4ef3085.toInt() + x3).rotateLeft(16)
        b = c + (b + (c xor d xor a) + 0x04881d05 + x6).rotateLeft(23)
        a = b + (a + (b xor c xor d) + 0xd9d4d039.toInt() + x9).rotateLeft(4)
        d = a + (d + (a xor b xor c) + 0xe6db99e5.toInt() + x12).rotateLeft(11)
        c = d + (c + (d xor a xor b) + 0x1fa27cf8 + x15).rotateLeft(16)
        b = c + (b + (c xor d xor a) + 0xc4ac5665.toInt() + x2).rotateLeft(23)
        // round 4
        a = b + (a + (c xor (b or d.inv())) + 0xf4292244.toInt() + x0).rotateLeft(6)
        d = a + (d + (b xor (a or c.inv())) + 0x432aff97 + x7).rotateLeft(10)
        c = d + (c + (a xor (d or b.inv())) + 0xab9423a7.toInt() + x14).rotateLeft(15)
        b = c + (b + (d xor (c or a.inv())) + 0xfc93a039.toInt() + x5).rotateLeft(21)
        a = b + (a + (c xor (b or d.inv())) + 0x655b59c3 + x12).rotateLeft(6)
        d = a + (d + (b xor (a or c.inv())) + 0x8f0ccc92.toInt() + x3).rotateLeft(10)
        c = d + (c + (a xor (d or b.inv())) + 0xffeff47d.toInt() + x10).rotateLeft(15)
        b = c + (b + (d xor (c or a.inv())) + 0x85845dd1.toInt() + x1).rotateLeft(21)
        a = b + (a + (c xor (b or d.inv())) + 0x6fa87e4f + x8).rotateLeft(6)
        d = a + (d + (b xor (a or c.inv())) + 0xfe2ce6e0.toInt() + x15).rotateLeft(10)
        c = d + (c + (a xor (d or b.inv())) + 0xa3014314.toInt() + x6).rotateLeft(15)
        b = c + (b + (d xor (c or a.inv())) + 0x4e0811a1 + x13).rotateLeft(21)
        a = b + (a + (c xor (b or d.inv())) + 0xf7537e82.toInt() + x4).rotateLeft(6)
        d = a + (d + (b xor (a or c.inv())) + 0xbd3af235.toInt() + x11).rotateLeft(10)
        c = d + (c + (a xor (d or b.inv())) + 0x2ad7d2bb + x2).rotateLeft(15)
        b = c + (b + (d xor (c or a.inv())) + 0xeb86d391.toInt() + x9).rotateLeft(21)

        // 64 steps is a multiple of 4, so the names are back in place
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
