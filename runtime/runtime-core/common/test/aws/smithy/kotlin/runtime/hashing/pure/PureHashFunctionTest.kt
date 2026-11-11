/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.hashing.hmac
import aws.smithy.kotlin.runtime.hashing.isSupportedForFlexibleChecksums
import aws.smithy.kotlin.runtime.hashing.resolveChecksumAlgorithmHeaderName
import aws.smithy.kotlin.runtime.hashing.toBusinessMetric
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Sizes straddle the 56-byte padding boundary and the 64-byte block boundary
private val sizes = listOf(0, 1, 55, 56, 57, 63, 64, 65, 127, 128, 1000, 100_003)

class PureHashFunctionTest {
    private val algorithms = listOf("crc32", "crc32c", "md5", "sha1", "sha256")

    @Test
    fun testMatchesRuntimeSingleUpdate() {
        val random = Random(1)
        for (algorithm in algorithms) {
            val runtime = hashFunction(RUNTIME, algorithm)
            val pure = hashFunction(PURE, algorithm)
            for (size in sizes) {
                val input = random.nextBytes(size)
                runtime.update(input)
                pure.update(input)
                assertContentEquals(runtime.digest(), pure.digest(), "$algorithm, size=$size")
            }
        }
    }

    @Test
    fun testMatchesRuntimeChunkedUpdatesWithOffsets() {
        val random = Random(2)
        val input = random.nextBytes(10_000)
        for (algorithm in algorithms) {
            val runtime = hashFunction(RUNTIME, algorithm)
            val pure = hashFunction(PURE, algorithm)
            var offset = 0
            while (offset < input.size) {
                val length = minOf(random.nextInt(0, 200), input.size - offset)
                runtime.update(input, offset, length)
                pure.update(input, offset, length)
                offset += length
            }
            assertContentEquals(runtime.digest(), pure.digest(), algorithm)
        }
    }

    @Test
    fun testResetDiscardsState() {
        for (algorithm in algorithms) {
            val pure = hashFunction(PURE, algorithm)
            pure.update(byteArrayOf(1, 2, 3))
            pure.reset()
            pure.update(byteArrayOf(4, 5, 6))
            val runtime = hashFunction(RUNTIME, algorithm).apply { update(byteArrayOf(4, 5, 6)) }
            assertContentEquals(runtime.digest(), pure.digest(), algorithm)
        }
    }

    @Test
    fun testHmacMatchesRuntime() {
        val random = Random(3)
        for (algorithm in listOf("md5", "sha1", "sha256")) {
            for (keySize in listOf(0, 16, 63, 64, 65, 100)) {
                val key = random.nextBytes(keySize)
                val message = random.nextBytes(1000)
                assertContentEquals(
                    hmac(key, message, hashFunction(RUNTIME, algorithm)),
                    hmac(key, message, hashFunction(PURE, algorithm)),
                    "$algorithm, keySize=$keySize",
                )
            }
        }
    }

    @Test
    fun testCrcChorbaCutoffsAndOffsets() {
        // CRC32 switches from the table loop to Chorba above 72 bytes, and Chorba's main loop (320 bytes per pass) runs
        // while more than 360 bytes remain, then a 72-byte loop and the table tail. 680 bytes take one main-loop pass,
        // 681 two and 1100 three. Offsets 1-7 put the 8-byte reads off alignment.
        val random = Random(4)
        val input = random.nextBytes(1200)
        val lengths = listOf(71, 72, 73, 104, 105, 360, 361, 392, 393, 424, 425, 600, 680, 681, 1100)
        for (algorithm in listOf("crc32", "crc32c")) {
            for (length in lengths) {
                for (offset in 0..7) {
                    val expected = bitwiseCrc(algorithm, input, offset, length)
                    val pure = hashFunction(PURE, algorithm).apply { update(input, offset, length) }.digest()
                    assertContentEquals(expected, pure, "$algorithm, offset=$offset, length=$length")
                }
            }
        }
    }

    @Test
    fun testCrcMatchesBitwiseReference() {
        // runtime-core's CRC32C shares its table code with PureCrc32c, so also check both CRCs against a bit-at-a-time
        // implementation
        val random = Random(5)
        for (algorithm in listOf("crc32", "crc32c")) {
            for (size in sizes) {
                val input = random.nextBytes(size)
                val pure = hashFunction(PURE, algorithm).apply { update(input) }.digest()
                assertContentEquals(bitwiseCrc(algorithm, input, 0, size), pure, "$algorithm, size=$size")
            }
        }
    }

    @Test
    fun testRejectsInvalidRanges() {
        val input = ByteArray(10)
        val invalid = listOf(
            -1 to 0,
            0 to -1,
            11 to 0, // offset past the end, even with nothing to read
            5 to 6,
            5 to Int.MAX_VALUE, // offset + length overflows
            Int.MAX_VALUE to 1,
            1 shl 30 to (1 shl 30),
        )
        for (algorithm in algorithms) {
            val pure = hashFunction(PURE, algorithm).apply { update(byteArrayOf(1, 2, 3)) }
            for ((offset, length) in invalid) {
                assertFailsWith<IllegalArgumentException>("$algorithm, offset=$offset, length=$length") {
                    pure.update(input, offset, length)
                }
            }
            // The rejected calls must not have changed the state
            val expected = hashFunction(RUNTIME, algorithm).apply { update(byteArrayOf(1, 2, 3)) }.digest()
            assertContentEquals(expected, pure.digest(), algorithm)

            // Empty slices at either end are valid
            hashFunction(PURE, algorithm).apply {
                update(input, 0, 0)
                update(input, 10, 0)
            }
        }
    }

    @Test
    fun testFlexibleChecksumSupportMatchesRuntime() {
        for (algorithm in algorithms + "md5") {
            val runtime = hashFunction(RUNTIME, algorithm)
            val pure = hashFunction(PURE, algorithm)
            assertEquals(runtime.isSupportedForFlexibleChecksums, pure.isSupportedForFlexibleChecksums, algorithm)
            assertEquals(runtime.resolveChecksumAlgorithmHeaderName(), pure.resolveChecksumAlgorithmHeaderName(), algorithm)
            if (runtime.isSupportedForFlexibleChecksums) assertEquals(runtime.toBusinessMetric(), pure.toBusinessMetric(), algorithm)
        }
        assertEquals(0xcbf43926U, pureCrc32().apply { update("123456789".encodeToByteArray()) }.digestValue())
    }
}

private val CRC_POLYNOMIALS = mapOf("crc32" to 0xEDB88320.toInt(), "crc32c" to 0x82F63B78.toInt())

/** Reflected CRC, one bit at a time, as big-endian digest bytes like [HashFunction.digest] */
private fun bitwiseCrc(algorithm: String, input: ByteArray, offset: Int, length: Int): ByteArray {
    val polynomial = CRC_POLYNOMIALS.getValue(algorithm)
    var crc = -1
    for (i in offset until offset + length) {
        crc = crc xor (input[i].toInt() and 0xff)
        repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor polynomial else crc ushr 1 }
    }
    val value = crc.inv()
    return byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
}
