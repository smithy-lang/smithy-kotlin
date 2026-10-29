/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.hashing.toHashFunctionOrThrow

/** runtime-core's default implementations (JVM stdlib, CRT, or Kotlin depending on algorithm/platform) */
internal const val RUNTIME = "runtime"

/** The pure-Kotlin implementations in this package */
internal const val PURE = "pure"

internal fun hashFunction(implementation: String, algorithm: String): HashFunction = when (implementation) {
    RUNTIME -> algorithm.toHashFunctionOrThrow()
    PURE -> when (algorithm) {
        "crc32" -> pureCrc32()
        "crc32c" -> pureCrc32c()
        else -> pureMerkleDamgardHashSupplier(algorithm)()
    }
    else -> error("Unknown implementation $implementation")
}

/** Supplies the [PURE] block hash for [algorithm], as needed by [PureHmac] */
internal fun pureMerkleDamgardHashSupplier(algorithm: String): () -> MerkleDamgardHash = when (algorithm) {
    "md5" -> ::PureMd5
    "sha1" -> ::PureSha1
    "sha256" -> ::PureSha256
    else -> error("Unknown algorithm $algorithm")
}

internal fun pureHmacFor(algorithm: String, key: ByteArray): PureHmac = PureHmac(key, pureMerkleDamgardHashSupplier(algorithm)())

/**
 * Variable-time k * G by affine double-and-add, as a test reference for [baseMultiplyAffineX]. It shares only the field
 * arithmetic with the code under test (which [PureEcdsaOptimizedTest] checks separately), not the point formulas or the
 * comb table. Returns the affine x-coordinate.
 */
internal fun referenceBaseMultiplyAffineX(k: IntArray): IntArray {
    val fp = SolinasP256Field
    val gx = limbsOf("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296")
    val gy = limbsOf("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
    val three = IntArray(8).apply { this[0] = 3 }

    // null is the point at infinity
    fun add(p: Pair<IntArray, IntArray>?, q: Pair<IntArray, IntArray>?): Pair<IntArray, IntArray>? {
        if (p == null) return q
        if (q == null) return p
        val (x1, y1) = p
        val (x2, y2) = q
        val lambda = if (x1.contentEquals(x2)) {
            if (fp.add(y1, y2).all { it == 0 }) return null // q = -p
            // (3x^2 + a) / 2y with a = -3
            fp.multiply(fp.subtract(fp.multiply(three, fp.square(x1)), three), fp.invert(fp.add(y1, y1)))
        } else {
            fp.multiply(fp.subtract(y2, y1), fp.invert(fp.subtract(x2, x1)))
        }
        val x3 = fp.subtract(fp.subtract(fp.square(lambda), x1), x2)
        return x3 to fp.subtract(fp.multiply(lambda, fp.subtract(x1, x3)), y1)
    }

    var acc: Pair<IntArray, IntArray>? = null
    for (bit in 255 downTo 0) {
        acc = add(acc, acc)
        if ((k[bit ushr 5] ushr (bit and 31)) and 1 == 1) acc = add(acc, gx to gy)
    }
    return checkNotNull(acc).first
}

/** Big-endian hex (64 digits) -> 8 little-endian 32-bit limbs */
@OptIn(ExperimentalStdlibApi::class)
internal fun limbsOf(hex: String): IntArray {
    val bytes = hex.padStart(64, '0').hexToByteArray()
    return IntArray(8) { bytes.getIntBe(28 - 4 * it) }
}
