/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.hashing.Crc32Base

/**
 * Pure-Kotlin CRC32 (IEEE 802.3, reflected polynomial 0xEDB88320). The bulk loop is the table-free "Chorba" algorithm
 * (Sam Russell, 2024, https://arxiv.org/abs/2412.16398), ported from zlib-ng's 64-bit
 * `crc32_chorba_small_nondestructive` (arch/generic/crc32_chorba_c.c). The bulk is reduced with shifts/XORs only;
 * the final 41..72 bytes and inputs of 72 bytes or fewer go through a slicing-by-8 table loop.
 */
internal class PureCrc32 : Crc32Base() {
    private var crc = -1 // bit-inverted running value

    // Scratch for the Chorba tail (zlib-ng's `final[9]`); reused to avoid a per-call allocation
    private val tail = ByteArray(72)

    override fun update(input: ByteArray, offset: Int, length: Int) {
        checkRange(input.size, offset, length)
        crc = if (length > CHORBA_SMALL_THRESHOLD) {
            chorba(crc, input, offset, offset + length)
        } else {
            tableUpdate(crc, input, offset, length)
        }
    }

    private fun chorba(c: Int, input: ByteArray, offset: Int, end: Int): Int {
        var n1 = c.toLong() and 0xffffffffL
        var n2 = 0L
        var n3 = 0L
        var n4 = 0L
        var n5 = 0L
        var p = offset

        // `end - p`, not `p + 360 < end`: the sum can overflow for slices near the end of a huge array
        while (end - p > 360) {
            val k1 = input.getLongLe(p) xor n1
            val k2 = input.getLongLe(p + 8) xor n2
            val k3 = input.getLongLe(p + 16) xor n3
            val k4 = input.getLongLe(p + 24) xor n4
            val k5 = input.getLongLe(p + 32) xor n5
            val k6 = input.getLongLe(p + 40)
            val k7 = input.getLongLe(p + 48) xor k1
            val k8 = input.getLongLe(p + 56) xor k2
            p += 64
            // words 0-3
            run {
                n5 = 0L // chorba5 already consumed n5
                val in1 = input.getLongLe(p) xor k3
                val in2 = input.getLongLe(p + 8) xor k4 xor k1
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor k5 xor k2 xor k1
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor k6 xor k3 xor k2
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
            // words 4-7
            run {
                val in1 = input.getLongLe(p) xor n1 xor k7 xor k4 xor k3
                val in2 = input.getLongLe(p + 8) xor n2 xor k8 xor k5 xor k4
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor n3 xor k6 xor k5
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor n4 xor k7 xor k6
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
            // words 8-11
            run {
                val in1 = input.getLongLe(p) xor n1 xor k8 xor k7 xor k1
                val in2 = input.getLongLe(p + 8) xor n2 xor k8 xor k2
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor n3 xor k3
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor n4 xor k4
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
            // words 12-15
            run {
                val in1 = input.getLongLe(p) xor n1 xor k5 xor k1
                val in2 = input.getLongLe(p + 8) xor n2 xor k6 xor k2 xor k1
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor n3 xor k7 xor k3 xor k2 xor k1
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor n4 xor k8 xor k4 xor k3 xor k2
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
            // words 16-19
            run {
                val in1 = input.getLongLe(p) xor n1 xor k5 xor k4 xor k3 xor k1
                val in2 = input.getLongLe(p + 8) xor n2 xor k6 xor k5 xor k4 xor k1 xor k2
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor n3 xor k7 xor k6 xor k5 xor k2 xor k3
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor n4 xor k8 xor k7 xor k6 xor k3 xor k4 xor k1
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
            // words 20-23
            run {
                val in1 = input.getLongLe(p) xor n1 xor k8 xor k7 xor k4 xor k5 xor k2 xor k1
                val in2 = input.getLongLe(p + 8) xor n2 xor k8 xor k5 xor k6 xor k3 xor k2
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor n3 xor k7 xor k6 xor k4 xor k3 xor k1
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor n4 xor k8 xor k7 xor k5 xor k4 xor k2 xor k1
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
            // words 24-27
            run {
                val in1 = input.getLongLe(p) xor n1 xor k8 xor k6 xor k5 xor k3 xor k2 xor k1
                val in2 = input.getLongLe(p + 8) xor n2 xor k7 xor k6 xor k4 xor k3 xor k2
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor n3 xor k8 xor k7 xor k5 xor k4 xor k3
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor n4 xor k8 xor k6 xor k5 xor k4
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
            // words 28-31
            run {
                val in1 = input.getLongLe(p) xor n1 xor k7 xor k6 xor k5
                val in2 = input.getLongLe(p + 8) xor n2 xor k8 xor k7 xor k6
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor n3 xor k8 xor k7
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor n4 xor k8
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
        }

        while (end - p > 72) {
            run {
                val in1 = input.getLongLe(p) xor n1
                val in2 = input.getLongLe(p + 8) xor n2
                val a1 = (in1 shl 17) xor (in1 shl 55)
                val a2 = (in1 ushr 47) xor (in1 ushr 9) xor (in1 shl 19)
                val a3 = (in1 ushr 45) xor (in1 shl 44)
                val a4 = in1 ushr 20
                val b1 = (in2 shl 17) xor (in2 shl 55)
                val b2 = (in2 ushr 47) xor (in2 ushr 9) xor (in2 shl 19)
                val b3 = (in2 ushr 45) xor (in2 shl 44)
                val b4 = in2 ushr 20
                val in3 = input.getLongLe(p + 16) xor a1 xor n3
                val in4 = input.getLongLe(p + 24) xor a2 xor b1 xor n4
                val c1 = (in3 shl 17) xor (in3 shl 55)
                val c2 = (in3 ushr 47) xor (in3 ushr 9) xor (in3 shl 19)
                val c3 = (in3 ushr 45) xor (in3 shl 44)
                val c4 = in3 ushr 20
                val d1 = (in4 shl 17) xor (in4 shl 55)
                val d2 = (in4 ushr 47) xor (in4 ushr 9) xor (in4 shl 19)
                val d3 = (in4 ushr 45) xor (in4 shl 44)
                val d4 = in4 ushr 20
                val t1 = n5 xor a3 xor b2 xor c1
                n2 = a4 xor b3 xor c2 xor d1
                n3 = b4 xor c3 xor d2
                n4 = c4 xor d3
                n5 = d4
                n1 = t1
                p += 32
            }
        }

        // Remaining 41..72 bytes, with the five pending fold words XORed into the first 40, finish via the table loop
        val rem = end - p
        input.copyInto(tail, 0, p, end)
        tail.xorLongLe(0, n1)
        tail.xorLongLe(8, n2)
        tail.xorLongLe(16, n3)
        tail.xorLongLe(24, n4)
        tail.xorLongLe(32, n5)
        return tableUpdate(0, tail, 0, rem).also { tail.fill(0) } // don't keep message bytes around
    }

    override fun digestValue(): UInt = crc.inv().toUInt().also { reset() }

    override fun reset() {
        crc = -1
    }

    private companion object {
        // 8 consecutive 256-entry tables: T[k * 256 + b] is the CRC of byte b followed by k zero bytes
        val T: IntArray = IntArray(8 * 256).also { t ->
            for (i in 0 until 256) {
                var c = i
                repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
                t[i] = c
            }
            for (k in 1 until 8) {
                for (i in 0 until 256) {
                    val prev = t[(k - 1) * 256 + i]
                    t[k * 256 + i] = (prev ushr 8) xor t[prev and 0xff]
                }
            }
        }

        /** The baseline slicing-by-8 loop, as a pure function of the running (bit-inverted) register. */
        fun tableUpdate(crc: Int, input: ByteArray, offset: Int, length: Int): Int {
            // Hoisted: avoids a companion access (and, on Kotlin/Native, its init check) per table lookup
            val t = T
            var c = crc
            var off = offset
            var len = length

            while (len >= 8) {
                val one = c xor input.getIntLe(off)
                c = t[7 * 256 + (one and 0xff)] xor
                    t[6 * 256 + ((one ushr 8) and 0xff)] xor
                    t[5 * 256 + ((one ushr 16) and 0xff)] xor
                    t[4 * 256 + (one ushr 24)] xor
                    t[3 * 256 + (input[off + 4].toInt() and 0xff)] xor
                    t[2 * 256 + (input[off + 5].toInt() and 0xff)] xor
                    t[1 * 256 + (input[off + 6].toInt() and 0xff)] xor
                    t[input[off + 7].toInt() and 0xff]
                off += 8
                len -= 8
            }

            while (len > 0) {
                c = (c ushr 8) xor t[(c xor input[off].toInt()) and 0xff]
                off++
                len--
            }
            return c
        }
    }
}

// zlib-ng's CHORBA_SMALL_THRESHOLD for 64-bit words. Top-level because a companion `const` is a public JVM field.
private const val CHORBA_SMALL_THRESHOLD = 72

private fun ByteArray.getLongLe(i: Int): Long = (getIntLe(i).toLong() and 0xffffffffL) or (getIntLe(i + 4).toLong() shl 32)

private fun ByteArray.xorLongLe(i: Int, value: Long) {
    for (k in 0 until 8) {
        this[i + k] = (this[i + k].toInt() xor (value ushr (8 * k)).toInt()).toByte()
    }
}
