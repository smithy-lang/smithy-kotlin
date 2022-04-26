/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.encodeToHex
import kotlin.test.Test
import kotlin.test.assertEquals

// Test vectors from https://www.di-mgt.com.au/sha_testvectors.html
class Sha1Test {
    private fun assertShaHexEqual(input: String, sha: String, repeat: Int = 1) {
        val expected = sha.replace(" ", "")
        val hash = Sha1()
        val chunk = input.encodeToByteArray()
        for (i in 0 until repeat) {
            hash.update(chunk)
        }
        val actual = hash.digest().encodeToHex()
        assertEquals(expected, actual)
    }

    @Test
    fun testVector1() {
        val input = "abc"
        val expected = "a9993e36 4706816a ba3e2571 7850c26c 9cd0d89d"
        assertShaHexEqual(input, expected)
    }
    @Test
    fun testVector2() {
        val input = ""
        val expected = "da39a3ee 5e6b4b0d 3255bfef 95601890 afd80709"
        assertShaHexEqual(input, expected)
    }
    @Test
    fun testVector3() {
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        val expected = "84983e44 1c3bd26e baae4aa1 f95129e5 e54670f1"
        assertShaHexEqual(input, expected)
    }
    @Test
    fun testVector4() {
        val input = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
        val expected = "a49b2446 a02c645b f419f995 b6709125 3a04a259"
        assertShaHexEqual(input, expected)
    }

    @Test
    fun testVector5() {
        val input = "a"
        val expected = "34aa973c d4c4daa4 f61eeb2b dbad2731 6534016f"
        assertShaHexEqual(input, expected, 1_000_000)
    }

    @Test
    fun testVector6() {
        val input = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno"
        val expected = "7789f0c9 ef7bfc40 d9331114 3dfbe69e 2017f592"
        assertShaHexEqual(input, expected, 16_777_216)
    }
}
