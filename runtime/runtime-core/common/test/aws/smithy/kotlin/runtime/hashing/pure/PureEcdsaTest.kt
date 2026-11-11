/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class PureEcdsaTest {
    // RFC 6979 appendix A.2.5 (P-256 with SHA-256)
    private val privateKey = "c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721".hexToByteArray()

    @Test
    fun testRfc6979Sample() {
        val r = "efd48b2aacb6a8fd1140dd9cd45e81d69d2c877b56aaf991c34d0ea84eaf3716"
        val s = "f7cb1c942d657c41d436c7a1b6e29f65f3e900dbb9aff4064dc4ab2f843acda8"
        val expected = "3046" + "022100$r" + "022100$s"
        assertEquals(expected, pureEcdsaSecp256r1(privateKey, "sample".encodeToByteArray()).toHexString())
    }

    @Test
    fun testRfc6979Test() {
        val r = "f1abb023518351cd71d881567b1ea663ed3efcf6c5132b354f28d3b0b7d38367"
        val s = "019f4113742a2b14bd25926b49c649155f267e60d3814b4c0cc84250e46f0083"
        val expected = "3045" + "022100$r" + "0220$s"
        assertEquals(expected, pureEcdsaSecp256r1(privateKey, "test".encodeToByteArray()).toHexString())
    }

    @Test
    fun testAcceptsSignedBigIntegerKeyEncoding() {
        // e.g. java.math.BigInteger.toByteArray() prepends a 0x00 sign byte when the high bit is set
        val message = "sample".encodeToByteArray()
        assertEquals(
            pureEcdsaSecp256r1(privateKey, message).toHexString(),
            pureEcdsaSecp256r1(byteArrayOf(0) + privateKey, message).toHexString(),
        )
    }
}
