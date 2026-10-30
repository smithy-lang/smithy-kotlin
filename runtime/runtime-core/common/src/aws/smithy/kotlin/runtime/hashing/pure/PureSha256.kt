/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

/**
 * Pure-Kotlin SHA-256 (FIPS 180-4). The companion `K` table and the `w`/`h` field arrays are read into locals once per
 * [processBlocks] call, so the hot loops don't reload them through `this`/the companion. The message schedule is
 * expanded inside the round loop.
 */
internal class PureSha256 : MerkleDamgardHash(bigEndianLength = true) {
    override val digestSizeBytes: Int = 32

    private val h = IntArray(8)
    private val w = IntArray(64)

    init {
        resetState()
    }

    override fun resetState() {
        H0.copyInto(h)
    }

    override fun processBlocks(input: ByteArray, offset: Int, count: Int) {
        val k = K
        val w = w
        val h = h

        var off = offset
        repeat(count) {
            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]

            // Each schedule word is computed in the round that consumes it, not in a separate pass. Two loops keep the
            // "read input vs expand schedule" choice out of the round body.
            for (i in 0 until 16) {
                val wi = input.getIntBe(off + 4 * i)
                w[i] = wi

                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + k[i] + wi
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                hh = g
                g = f
                f = e
                e = d + t1
                d = c
                c = b
                b = a
                a = t1 + s0 + maj
            }
            for (i in 16 until 64) {
                val w15 = w[i - 15]
                val w2 = w[i - 2]
                val sw0 = w15.rotateRight(7) xor w15.rotateRight(18) xor (w15 ushr 3)
                val sw1 = w2.rotateRight(17) xor w2.rotateRight(19) xor (w2 ushr 10)
                val wi = w[i - 16] + sw0 + w[i - 7] + sw1
                w[i] = wi

                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + k[i] + wi
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                hh = g
                g = f
                f = e
                e = d + t1
                d = c
                c = b
                b = a
                a = t1 + s0 + maj
            }

            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
            off += 64
        }
    }

    override fun writeDigest(out: ByteArray) {
        for (i in 0 until 8) out.putIntBe(4 * i, h[i])
    }

    override fun newInstance(): MerkleDamgardHash = PureSha256()

    override val scratch: IntArray get() = w

    override fun copyChainingStateFrom(other: MerkleDamgardHash) {
        (other as PureSha256).h.copyInto(h)
    }

    private companion object {
        val H0 = longArrayOf(
            0x6a09e667,
            0xbb67ae85,
            0x3c6ef372,
            0xa54ff53a,
            0x510e527f,
            0x9b05688c,
            0x1f83d9ab,
            0x5be0cd19,
        ).let { k -> IntArray(k.size) { k[it].toInt() } }

        val K = longArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
        ).let { k -> IntArray(k.size) { k[it].toInt() } }
    }
}
