/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

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
    private val algorithms = listOf("md5", "sha1", "sha256")

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
        for (algorithm in algorithms) {
            val runtime = hashFunction(RUNTIME, algorithm)
            val pure = hashFunction(PURE, algorithm)
            assertEquals(runtime.isSupportedForFlexibleChecksums, pure.isSupportedForFlexibleChecksums, algorithm)
            assertEquals(runtime.resolveChecksumAlgorithmHeaderName(), pure.resolveChecksumAlgorithmHeaderName(), algorithm)
            if (runtime.isSupportedForFlexibleChecksums) assertEquals(runtime.toBusinessMetric(), pure.toBusinessMetric(), algorithm)
        }
    }
}
