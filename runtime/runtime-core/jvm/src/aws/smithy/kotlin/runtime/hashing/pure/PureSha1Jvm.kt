/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

internal actual val SHA1_SCHEDULE_SIZE: Int = 80

/**
 * JVM SHA-1 block function: rolled round loops over an 80-word schedule array. Keeping the schedule in locals (the
 * native version) measured 0.72-0.79x on the JVM.
 */
internal actual fun sha1ProcessBlocks(state: IntArray, schedule: IntArray, input: ByteArray, offset: Int, count: Int) {
    val w = schedule
    var s0 = state[0]
    var s1 = state[1]
    var s2 = state[2]
    var s3 = state[3]
    var s4 = state[4]

    var off = offset
    repeat(count) {
        for (i in 0 until 16) w[i] = input.getIntBe(off + 4 * i)
        for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)

        var a = s0
        var b = s1
        var c = s2
        var d = s3
        var e = s4

        for (i in 0 until 20) {
            val t = a.rotateLeft(5) + ((b and c) or (b.inv() and d)) + e + 0x5a827999 + w[i]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = t
        }
        for (i in 20 until 40) {
            val t = a.rotateLeft(5) + (b xor c xor d) + e + 0x6ed9eba1 + w[i]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = t
        }
        for (i in 40 until 60) {
            val t = a.rotateLeft(5) + ((b and c) or (b and d) or (c and d)) + e + SHA1_K3 + w[i]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = t
        }
        for (i in 60 until 80) {
            val t = a.rotateLeft(5) + (b xor c xor d) + e + SHA1_K4 + w[i]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = t
        }

        s0 += a
        s1 += b
        s2 += c
        s3 += d
        s4 += e
        off += 64
    }

    state[0] = s0
    state[1] = s1
    state[2] = s2
    state[3] = s3
    state[4] = s4
}
