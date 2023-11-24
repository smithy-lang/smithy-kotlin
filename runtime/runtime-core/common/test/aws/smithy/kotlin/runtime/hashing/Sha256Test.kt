/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import kotlin.test.Test
import kotlin.test.assertEquals

// Test vectors from https://www.di-mgt.com.au/sha_testvectors.html
class Sha256Test {
    private fun assertSha256HexEqual(input: String, sha: String, repeat: Int = 1) {
        val expected = sha.replace(" ", "")
        val hash = Sha256()
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
        val expected = "ba7816bf 8f01cfea 414140de 5dae2223 b00361a3 96177a9c b410ff61 f20015ad"
        assertSha256HexEqual(input, expected)
    }

    @Test
    fun testVector2() {
        val input = ""
        val expected = "e3b0c442 98fc1c14 9afbf4c8 996fb924 27ae41e4 649b934c a495991b 7852b855"
        assertSha256HexEqual(input, expected)
    }

    @Test
    fun testVector3() {
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        val expected = "248d6a61 d20638b8 e5c02693 0c3e6039 a33ce459 64ff2167 f6ecedd4 19db06c1"
        assertSha256HexEqual(input, expected)
    }

    @Test
    fun testVector4() {
        val input = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
        val expected = "cf5b16a7 78af8380 036ce59e 7b049237 0b249b11 e8f07a51 afac4503 7afee9d1"
        assertSha256HexEqual(input, expected)
    }

    @Test
    fun testVector5() {
        val input = "a"
        val expected = "cdc76e5c 9914fb92 81a1c7e2 84d73e67 f1809a48 a497200e 046d39cc c7112cd0"
        assertSha256HexEqual(input, expected, 1_000_000)
    }

    @Test
    fun testVector6() {
        val input = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno"
        val expected = "50e72a0e 26442fe2 552dc393 8ac58658 228c0cbf b1d2ca87 2ae43526 6fcd055e"
        assertSha256HexEqual(input, expected, 16_777_216)
    }
}
