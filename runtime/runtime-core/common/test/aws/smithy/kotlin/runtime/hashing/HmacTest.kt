/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.encodeToHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PANGRAM = "The quick brown fox jumps over the lazy dog"

class HmacTest {
    // Pangram test data adapted from https://en.wikipedia.org/wiki/HMAC#Examples

    @Test
    fun testPangramMd5() {
        assertEquals("80070713463e7749b90c2dc24911e275", hmacTest("key", PANGRAM, ::Md5))
    }

    @Test
    fun testPangramSha1() {
        assertEquals("de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9", hmacTest("key", PANGRAM, ::Sha1))
    }

    @Test
    fun testPangramSha256() {
        assertEquals(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            hmacTest("key", PANGRAM, ::Sha256),
        )

        assertEquals(
            "5597b93a2843078cbb0c920ae41dfe20f1685e10c67e423c11ab91adfc319d12",
            hmacTest(PANGRAM.repeat(2), "message", ::Sha256),
        )
    }

    // RFC test data adapted from https://datatracker.ietf.org/doc/html/rfc4231

    // https://datatracker.ietf.org/doc/html/rfc4231#section-4.2
    @Test
    fun testRfc4231Case1() {
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            hmacTest(bytes(20) { 0x0b }, "Hi There", ::Sha256),
        )
    }

    // https://datatracker.ietf.org/doc/html/rfc4231#section-4.3
    @Test
    fun testRfc4231Case2() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            hmacTest("Jefe", "what do ya want for nothing?", ::Sha256),
        )
    }

    // https://datatracker.ietf.org/doc/html/rfc4231#section-4.4
    @Test
    fun testRfc4231Case3() {
        assertEquals(
            "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe",
            hmacTest(bytes(20) { 0xaa }, bytes(50) { 0xdd }, ::Sha256),
        )
    }

    // https://datatracker.ietf.org/doc/html/rfc4231#section-4.5
    @Test
    fun testRfc4231Case4() {
        assertEquals(
            "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b",
            hmacTest(bytes(25) { it + 1 }, bytes(50) { 0xcd }, ::Sha256),
        )
    }

    // https://datatracker.ietf.org/doc/html/rfc4231#section-4.6
    @Test
    fun testRfc4231Case5() {
        // We don't support truncation directly as part of the hmac function so we'll just cheese it here by performing
        // a prefix check.
        val fullHash = hmacTest(bytes(20) { 0x0c }, "Test With Truncation", ::Sha256)
        assertTrue(fullHash.startsWith("a3b6167473100ee06e0c796c2955552b"), "Expected hash $fullHash to start with a3b6167473100ee06e0c796c2955552b but it did not")
    }

    // https://datatracker.ietf.org/doc/html/rfc4231#section-4.7
    @Test
    fun testRfc4231Case6() {
        assertEquals(
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
            hmacTest(bytes(131) { 0xaa }, "Test Using Larger Than Block-Size Key - Hash Key First", ::Sha256),
        )
    }

    // https://datatracker.ietf.org/doc/html/rfc4231#section-4.8
    @Test
    fun testRfc4231Case7() {
        val payload = "This is a test using a larger than block-size key and a larger than block-size data. The key " +
            "needs to be hashed before being used by the HMAC algorithm."
        assertEquals(
            "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2",
            hmacTest(bytes(131) { 0xaa }, payload, ::Sha256),
        )
    }
}

private fun bytes(length: Int, values: (Int) -> Int): ByteArray =
    ByteArray(length) { values(it).toByte() }

private fun hmacTest(key: String, message: String, hmacSupplier: HashSupplier): String =
    hmacTest(key.encodeToByteArray(), message, hmacSupplier)

private fun hmacTest(key: ByteArray, message: String, hmacSupplier: HashSupplier): String =
    hmacTest(key, message.encodeToByteArray(), hmacSupplier)

private fun hmacTest(key: ByteArray, message: ByteArray, hmacSupplier: HashSupplier): String =
    hmac(key, message, hmacSupplier).encodeToHex()
