/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.hashing.HashSupplier
import aws.smithy.kotlin.runtime.hashing.hmac
import kotlin.test.Test
import kotlin.test.assertEquals

// Known-answer vectors mirrored from runtime-core's hashing tests so the pure implementations are held to the same data

@OptIn(ExperimentalStdlibApi::class)
private fun HashFunction.hexDigest(input: ByteArray, repeat: Int = 1): String {
    repeat(repeat) { update(input) }
    return digest().toHexString()
}

private fun bytes(length: Int, values: (Int) -> Int): ByteArray = ByteArray(length) { values(it).toByte() }

class PureCrc32KnownVectorsTest {
    private val vectors = listOf(
        "foobar".encodeToByteArray() to 2666930069U,
        "123456789".encodeToByteArray() to 0xcbf43926U, // CRC-32/ISO-HDLC check value
        byteArrayOf(0) to 3523407757U,
        ByteArray(1024) { 0 } to 4021661486U,
        ByteArray(1024) { -1 } to 3090874356U,
    )

    @Test
    fun testVectors() {
        for ((input, expected) in vectors) {
            assertEquals(expected, PureCrc32().apply { update(input) }.digestValue())
        }
    }

    @Test
    fun testChainedChecksum() {
        val input = "foobar".encodeToByteArray()
        val crc = PureCrc32()
        crc.update(input, 0, 3)
        crc.update(input, 3, 3)
        assertEquals(2666930069U, crc.digestValue())
    }

    @Test
    fun testResetAndDigestResetValue() {
        val crc = PureCrc32()
        crc.update("foobar".encodeToByteArray())
        crc.reset()
        assertEquals(0U, crc.digestValue())

        crc.update("foobar".encodeToByteArray())
        assertEquals(2666930069U, crc.digestValue())
        assertEquals(0U, crc.digestValue())
    }
}

class PureMd5KnownVectorsTest {
    // https://www.ietf.org/rfc/rfc1321.txt
    private val vectors = listOf(
        "" to "d41d8cd98f00b204e9800998ecf8427e",
        "a" to "0cc175b9c0f1b6a831c399e269772661",
        "abc" to "900150983cd24fb0d6963f7d28e17f72",
        "message digest" to "f96b697d7cb7938d525a2f31aaf161d0",
        "abcdefghijklmnopqrstuvwxyz" to "c3fcd3d76192e4007dfb496cca67e13b",
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" to "d174ab98d277d9f5a5611c2c9f419d9f",
        "12345678901234567890123456789012345678901234567890123456789012345678901234567890" to "57edf4a22be3c955ac49da2e2107b67a",
    )

    @Test
    fun testRfc1321Vectors() {
        for ((input, expected) in vectors) {
            assertEquals(expected, PureMd5().hexDigest(input.encodeToByteArray()), "input=\"$input\"")
        }
    }

    @Test
    fun testKnownCollision() {
        val message1 = intArrayOf(
            0xd1, 0x31, 0xdd, 0x02, 0xc5, 0xe6, 0xee, 0xc4, 0x69, 0x3d, 0x9a, 0x06, 0x98, 0xaf, 0xf9, 0x5c,
            0x2f, 0xca, 0xb5, 0x87, 0x12, 0x46, 0x7e, 0xab, 0x40, 0x04, 0x58, 0x3e, 0xb8, 0xfb, 0x7f, 0x89,
            0x55, 0xad, 0x34, 0x06, 0x09, 0xf4, 0xb3, 0x02, 0x83, 0xe4, 0x88, 0x83, 0x25, 0x71, 0x41, 0x5a,
            0x08, 0x51, 0x25, 0xe8, 0xf7, 0xcd, 0xc9, 0x9f, 0xd9, 0x1d, 0xbd, 0xf2, 0x80, 0x37, 0x3c, 0x5b,
            0xd8, 0x82, 0x3e, 0x31, 0x56, 0x34, 0x8f, 0x5b, 0xae, 0x6d, 0xac, 0xd4, 0x36, 0xc9, 0x19, 0xc6,
            0xdd, 0x53, 0xe2, 0xb4, 0x87, 0xda, 0x03, 0xfd, 0x02, 0x39, 0x63, 0x06, 0xd2, 0x48, 0xcd, 0xa0,
            0xe9, 0x9f, 0x33, 0x42, 0x0f, 0x57, 0x7e, 0xe8, 0xce, 0x54, 0xb6, 0x70, 0x80, 0xa8, 0x0d, 0x1e,
            0xc6, 0x98, 0x21, 0xbc, 0xb6, 0xa8, 0x83, 0x93, 0x96, 0xf9, 0x65, 0x2b, 0x6f, 0xf7, 0x2a, 0x70,
        ).map { it.toByte() }.toByteArray()

        val message2 = intArrayOf(
            0xd1, 0x31, 0xdd, 0x02, 0xc5, 0xe6, 0xee, 0xc4, 0x69, 0x3d, 0x9a, 0x06, 0x98, 0xaf, 0xf9, 0x5c,
            0x2f, 0xca, 0xb5, 0x07, 0x12, 0x46, 0x7e, 0xab, 0x40, 0x04, 0x58, 0x3e, 0xb8, 0xfb, 0x7f, 0x89,
            0x55, 0xad, 0x34, 0x06, 0x09, 0xf4, 0xb3, 0x02, 0x83, 0xe4, 0x88, 0x83, 0x25, 0xf1, 0x41, 0x5a,
            0x08, 0x51, 0x25, 0xe8, 0xf7, 0xcd, 0xc9, 0x9f, 0xd9, 0x1d, 0xbd, 0x72, 0x80, 0x37, 0x3c, 0x5b,
            0xd8, 0x82, 0x3e, 0x31, 0x56, 0x34, 0x8f, 0x5b, 0xae, 0x6d, 0xac, 0xd4, 0x36, 0xc9, 0x19, 0xc6,
            0xdd, 0x53, 0xe2, 0x34, 0x87, 0xda, 0x03, 0xfd, 0x02, 0x39, 0x63, 0x06, 0xd2, 0x48, 0xcd, 0xa0,
            0xe9, 0x9f, 0x33, 0x42, 0x0f, 0x57, 0x7e, 0xe8, 0xce, 0x54, 0xb6, 0x70, 0x80, 0x28, 0x0d, 0x1e,
            0xc6, 0x98, 0x21, 0xbc, 0xb6, 0xa8, 0x83, 0x93, 0x96, 0xf9, 0x65, 0xab, 0x6f, 0xf7, 0x2a, 0x70,
        ).map { it.toByte() }.toByteArray()

        val expected = "79054025255fb1a26e4bc422aef54eb4"
        assertEquals(expected, PureMd5().hexDigest(message1))
        assertEquals(expected, PureMd5().hexDigest(message2))
    }
}

// https://www.di-mgt.com.au/sha_testvectors.html
private const val SHA_448_BITS = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
private const val SHA_896_BITS =
    "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
private const val SHA_EXTREMELY_LONG = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno"

private data class ShaVector(val input: String, val repeat: Int, val expected: String)

class PureSha1KnownVectorsTest {
    private val vectors = listOf(
        ShaVector("abc", 1, "a9993e364706816aba3e25717850c26c9cd0d89d"),
        ShaVector("", 1, "da39a3ee5e6b4b0d3255bfef95601890afd80709"),
        ShaVector(SHA_448_BITS, 1, "84983e441c3bd26ebaae4aa1f95129e5e54670f1"),
        ShaVector(SHA_896_BITS, 1, "a49b2446a02c645bf419f995b67091253a04a259"),
        ShaVector("a", 1_000_000, "34aa973cd4c4daa4f61eeb2bdbad27316534016f"),
        ShaVector(SHA_EXTREMELY_LONG, 16_777_216, "7789f0c9ef7bfc40d93311143dfbe69e2017f592"),
    )

    @Test
    fun testVectors() {
        for ((input, repeat, expected) in vectors) {
            assertEquals(expected, PureSha1().hexDigest(input.encodeToByteArray(), repeat), "input=\"$input\" x $repeat")
        }
    }
}

class PureSha256KnownVectorsTest {
    private val vectors = listOf(
        ShaVector("abc", 1, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
        ShaVector("", 1, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        ShaVector(SHA_448_BITS, 1, "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"),
        ShaVector(SHA_896_BITS, 1, "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1"),
        ShaVector("a", 1_000_000, "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"),
        ShaVector(SHA_EXTREMELY_LONG, 16_777_216, "50e72a0e26442fe2552dc3938ac58658228c0cbfb1d2ca872ae435266fcd055e"),
    )

    @Test
    fun testVectors() {
        for ((input, repeat, expected) in vectors) {
            assertEquals(expected, PureSha256().hexDigest(input.encodeToByteArray(), repeat), "input=\"$input\" x $repeat")
        }
    }
}

private const val PANGRAM = "The quick brown fox jumps over the lazy dog"
private const val RFC2202_LARGE_KEY = "Test Using Larger Than Block-Size Key - Hash Key First"
private const val RFC2202_LARGE_KEY_AND_DATA = "Test Using Larger Than Block-Size Key and Larger Than One Block-Size Data"

private data class HmacVector(val key: ByteArray, val data: ByteArray, val expected: String) {
    constructor(key: String, data: String, expected: String) : this(key.encodeToByteArray(), data.encodeToByteArray(), expected)
    constructor(key: ByteArray, data: String, expected: String) : this(key, data.encodeToByteArray(), expected)
}

@OptIn(ExperimentalStdlibApi::class)
class PureHmacKnownVectorsTest {
    private fun assertVectors(name: String, hash: HashSupplier, vectors: List<HmacVector>) {
        vectors.forEachIndexed { i, (key, data, expected) ->
            assertEquals(expected, hmac(key, data, hash).toHexString(), "$name case ${i + 1}")
        }
    }

    // https://en.wikipedia.org/wiki/HMAC#Examples
    @Test
    fun testPangram() {
        assertVectors("HMAC-MD5 pangram", ::PureMd5, listOf(HmacVector("key", PANGRAM, "80070713463e7749b90c2dc24911e275")))
        assertVectors("HMAC-SHA1 pangram", ::PureSha1, listOf(HmacVector("key", PANGRAM, "de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9")))
        assertVectors(
            "HMAC-SHA256 pangram",
            ::PureSha256,
            listOf(
                HmacVector("key", PANGRAM, "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"),
                HmacVector(PANGRAM.repeat(2), "message", "5597b93a2843078cbb0c920ae41dfe20f1685e10c67e423c11ab91adfc319d12"),
            ),
        )
    }

    // https://datatracker.ietf.org/doc/html/rfc4231 test cases 1-4, 6, 7 (case 5 is truncated; checked by prefix below)
    @Test
    fun testRfc4231Sha256() {
        assertVectors(
            "RFC 4231 HMAC-SHA256",
            ::PureSha256,
            listOf(
                HmacVector(bytes(20) { 0x0b }, "Hi There", "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"),
                HmacVector("Jefe", "what do ya want for nothing?", "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"),
                HmacVector(bytes(20) { 0xaa }, bytes(50) { 0xdd }, "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe"),
                HmacVector(bytes(25) { it + 1 }, bytes(50) { 0xcd }, "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b"),
                HmacVector(
                    bytes(131) { 0xaa },
                    "Test Using Larger Than Block-Size Key - Hash Key First",
                    "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
                ),
                HmacVector(
                    bytes(131) { 0xaa },
                    "This is a test using a larger than block-size key and a larger than block-size data. The key " +
                        "needs to be hashed before being used by the HMAC algorithm.",
                    "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2",
                ),
            ),
        )

        val truncated = hmac(bytes(20) { 0x0c }, "Test With Truncation".encodeToByteArray(), ::PureSha256).toHexString()
        assertEquals("a3b6167473100ee06e0c796c2955552b", truncated.take(32), "RFC 4231 HMAC-SHA256 case 5")
    }

    // https://datatracker.ietf.org/doc/html/rfc2202 test cases 1-7
    @Test
    fun testRfc2202Md5() {
        assertVectors(
            "RFC 2202 HMAC-MD5",
            ::PureMd5,
            listOf(
                HmacVector(bytes(16) { 0x0b }, "Hi There", "9294727a3638bb1c13f48ef8158bfc9d"),
                HmacVector("Jefe", "what do ya want for nothing?", "750c783e6ab0b503eaa86e310a5db738"),
                HmacVector(bytes(16) { 0xaa }, bytes(50) { 0xdd }, "56be34521d144c88dbb8c733f0e8b3f6"),
                HmacVector(bytes(25) { it + 1 }, bytes(50) { 0xcd }, "697eaf0aca3a3aea3a75164746ffaa79"),
                HmacVector(bytes(16) { 0x0c }, "Test With Truncation", "56461ef2342edc00f9bab995690efd4c"),
                HmacVector(bytes(80) { 0xaa }, RFC2202_LARGE_KEY, "6b1ab7fe4bd7bf8f0b62e6ce61b9d0cd"),
                HmacVector(bytes(80) { 0xaa }, RFC2202_LARGE_KEY_AND_DATA, "6f630fad67cda0ee1fb1f562db3aa53e"),
            ),
        )
    }

    @Test
    fun testRfc2202Sha1() {
        assertVectors(
            "RFC 2202 HMAC-SHA1",
            ::PureSha1,
            listOf(
                HmacVector(bytes(20) { 0x0b }, "Hi There", "b617318655057264e28bc0b6fb378c8ef146be00"),
                HmacVector("Jefe", "what do ya want for nothing?", "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79"),
                HmacVector(bytes(20) { 0xaa }, bytes(50) { 0xdd }, "125d7342b9ac11cd91a39af48aa17b4f63f175d3"),
                HmacVector(bytes(25) { it + 1 }, bytes(50) { 0xcd }, "4c9007f4026250c6bc8414f9bf50c86c2d7235da"),
                HmacVector(bytes(20) { 0x0c }, "Test With Truncation", "4c1a03424b55e07fe7f27be1d58bb9324a9a5a04"),
                HmacVector(bytes(80) { 0xaa }, RFC2202_LARGE_KEY, "aa4ae5e15272d00e95705637ce8a3b55ed402112"),
                HmacVector(bytes(80) { 0xaa }, RFC2202_LARGE_KEY_AND_DATA, "e8e99d0f45237d786d6bbaa7965c7808bbff1a91"),
            ),
        )
    }
}
