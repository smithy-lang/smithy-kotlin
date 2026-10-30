/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.hashing.hmac
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

// Sizes straddle the 56-byte padding boundary and the 64-byte block boundary
private val sizes = listOf(0, 1, 55, 56, 57, 63, 64, 65, 127, 128, 1000, 100_003)

private val blockHashes = listOf("md5", "sha1", "sha256")

private data class PureHmacVector(val algorithm: String, val key: ByteArray, val data: ByteArray, val expected: String)

private fun bytes(length: Int, values: (Int) -> Int): ByteArray = ByteArray(length) { values(it).toByte() }

private fun vector(algorithm: String, key: String, data: String, expected: String) = PureHmacVector(algorithm, key.encodeToByteArray(), data.encodeToByteArray(), expected)

private fun vector(algorithm: String, key: ByteArray, data: String, expected: String) = PureHmacVector(algorithm, key, data.encodeToByteArray(), expected)

private const val PANGRAM = "The quick brown fox jumps over the lazy dog"
private const val RFC2202_LARGE_KEY = "Test Using Larger Than Block-Size Key - Hash Key First"
private const val RFC2202_LARGE_KEY_AND_DATA = "Test Using Larger Than Block-Size Key and Larger Than One Block-Size Data"

// Same vectors as PureHmacKnownVectorsTest, run through PureHmac instead of runtime-core's hmac
private val hmacVectors = listOf(
    // https://en.wikipedia.org/wiki/HMAC#Examples
    vector("md5", "key", PANGRAM, "80070713463e7749b90c2dc24911e275"),
    vector("sha1", "key", PANGRAM, "de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9"),
    vector("sha256", "key", PANGRAM, "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"),
    vector("sha256", PANGRAM.repeat(2), "message", "5597b93a2843078cbb0c920ae41dfe20f1685e10c67e423c11ab91adfc319d12"),
    // https://datatracker.ietf.org/doc/html/rfc4231 test cases 1-4, 6, 7
    vector("sha256", bytes(20) { 0x0b }, "Hi There", "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"),
    vector("sha256", "Jefe", "what do ya want for nothing?", "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"),
    PureHmacVector("sha256", bytes(20) { 0xaa }, bytes(50) { 0xdd }, "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe"),
    PureHmacVector("sha256", bytes(25) { it + 1 }, bytes(50) { 0xcd }, "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b"),
    vector("sha256", bytes(131) { 0xaa }, RFC2202_LARGE_KEY, "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54"),
    vector(
        "sha256",
        bytes(131) { 0xaa },
        "This is a test using a larger than block-size key and a larger than block-size data. The key " +
            "needs to be hashed before being used by the HMAC algorithm.",
        "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2",
    ),
    // https://datatracker.ietf.org/doc/html/rfc2202 test cases 1-7
    vector("md5", bytes(16) { 0x0b }, "Hi There", "9294727a3638bb1c13f48ef8158bfc9d"),
    vector("md5", "Jefe", "what do ya want for nothing?", "750c783e6ab0b503eaa86e310a5db738"),
    PureHmacVector("md5", bytes(16) { 0xaa }, bytes(50) { 0xdd }, "56be34521d144c88dbb8c733f0e8b3f6"),
    PureHmacVector("md5", bytes(25) { it + 1 }, bytes(50) { 0xcd }, "697eaf0aca3a3aea3a75164746ffaa79"),
    vector("md5", bytes(16) { 0x0c }, "Test With Truncation", "56461ef2342edc00f9bab995690efd4c"),
    vector("md5", bytes(80) { 0xaa }, RFC2202_LARGE_KEY, "6b1ab7fe4bd7bf8f0b62e6ce61b9d0cd"),
    vector("md5", bytes(80) { 0xaa }, RFC2202_LARGE_KEY_AND_DATA, "6f630fad67cda0ee1fb1f562db3aa53e"),
    vector("sha1", bytes(20) { 0x0b }, "Hi There", "b617318655057264e28bc0b6fb378c8ef146be00"),
    vector("sha1", "Jefe", "what do ya want for nothing?", "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79"),
    PureHmacVector("sha1", bytes(20) { 0xaa }, bytes(50) { 0xdd }, "125d7342b9ac11cd91a39af48aa17b4f63f175d3"),
    PureHmacVector("sha1", bytes(25) { it + 1 }, bytes(50) { 0xcd }, "4c9007f4026250c6bc8414f9bf50c86c2d7235da"),
    vector("sha1", bytes(20) { 0x0c }, "Test With Truncation", "4c1a03424b55e07fe7f27be1d58bb9324a9a5a04"),
    vector("sha1", bytes(80) { 0xaa }, RFC2202_LARGE_KEY, "aa4ae5e15272d00e95705637ce8a3b55ed402112"),
    vector("sha1", bytes(80) { 0xaa }, RFC2202_LARGE_KEY_AND_DATA, "e8e99d0f45237d786d6bbaa7965c7808bbff1a91"),
)

@OptIn(ExperimentalStdlibApi::class)
class PureHmacTest {
    @Test
    fun testKnownVectors() {
        hmacVectors.forEachIndexed { i, (algorithm, key, data, expected) ->
            assertEquals(expected, pureHmacFor(algorithm, key).mac(data).toHexString(), "vector ${i + 1} ($algorithm)")
        }

        // RFC 4231 case 5 is truncated to 128 bits
        val truncated = pureHmacFor("sha256", bytes(20) { 0x0c }).mac("Test With Truncation".encodeToByteArray())
        assertEquals("a3b6167473100ee06e0c796c2955552b", truncated.toHexString().take(32), "RFC 4231 HMAC-SHA256 case 5")
    }

    @Test
    fun testMatchesRuntime() {
        val random = Random(5)
        for (algorithm in blockHashes) {
            for (keySize in listOf(0, 1, 32, 63, 64, 65, 131)) {
                val key = random.nextBytes(keySize)
                val pure = pureHmacFor(algorithm, key)
                for (size in sizes) {
                    val message = random.nextBytes(size)
                    assertContentEquals(
                        hmac(key, message, hashFunction(RUNTIME, algorithm)),
                        pure.mac(message),
                        "$algorithm, keySize=$keySize, size=$size",
                    )
                }
            }
        }
    }

    @Test
    fun testRepeatedAndInterleavedCalls() {
        val random = Random(6)
        for (algorithm in blockHashes) {
            val keyA = random.nextBytes(32)
            val keyB = random.nextBytes(100)
            val messages = sizes.map { random.nextBytes(it) }
            val a = pureHmacFor(algorithm, keyA)
            val b = pureHmacFor(algorithm, keyB)
            repeat(2) { round ->
                for (message in messages) {
                    val expectedA = hmac(keyA, message, hashFunction(RUNTIME, algorithm))
                    val expectedB = hmac(keyB, message, hashFunction(RUNTIME, algorithm))
                    // Same instance twice in a row, then interleaved with a second instance
                    assertContentEquals(expectedA, a.mac(message), "$algorithm A, round=$round, size=${message.size}")
                    assertContentEquals(expectedA, a.mac(message), "$algorithm A again, size=${message.size}")
                    assertContentEquals(expectedB, b.mac(message), "$algorithm B, size=${message.size}")
                    assertContentEquals(expectedA, a.mac(message), "$algorithm A after B, size=${message.size}")
                }
            }
        }
    }
}

class MerkleDamgardHashCopyTest {
    @Test
    fun testCopyAndRestoreAreIndependent() {
        val random = Random(7)
        for (algorithm in blockHashes) {
            for (prefixSize in listOf(0, 1, 63, 64, 65, 130)) {
                val prefix = random.nextBytes(prefixSize)
                val suffixA = random.nextBytes(random.nextInt(0, 200))
                val suffixB = random.nextBytes(random.nextInt(0, 200))
                fun expected(suffix: ByteArray) = hashFunction(RUNTIME, algorithm).apply {
                    update(prefix)
                    update(suffix)
                }.digest()
                val label = "$algorithm, prefix=$prefixSize"

                val original = pureMerkleDamgardHashSupplier(algorithm)().apply { update(prefix) }
                val copy = original.copy()
                val restored = pureMerkleDamgardHashSupplier(algorithm)().apply {
                    update(random.nextBytes(77)) // state that restoreFrom must fully overwrite
                    restoreFrom(original)
                }

                // Continue each with different data; none may observe another's updates
                original.update(suffixA)
                copy.update(suffixB)
                restored.update(suffixA)
                assertContentEquals(expected(suffixA), original.digest(), "$label, original")
                assertContentEquals(expected(suffixB), copy.digest(), "$label, copy")
                assertContentEquals(expected(suffixA), restored.digest(), "$label, restored")
            }
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
class PureHmacFactoryTest {
    @Test
    fun testPublicFactoriesMatchRuntime() {
        val key = ByteArray(40) { it.toByte() }
        val message = ByteArray(300) { (it * 7).toByte() }
        val factories = listOf("md5" to ::pureHmacMd5, "sha1" to ::pureHmacSha1, "sha256" to ::pureHmacSha256)
        for ((algorithm, factory) in factories) {
            assertContentEquals(hmac(key, message, hashFunction(RUNTIME, algorithm)), factory(key)(message), algorithm)
        }
    }

    @Test
    fun testHashArgumentIsNeitherUsedNorKept() {
        // Two HMACs from one pre-fed hash, which the caller then keeps using: no MAC may change, and the hash itself
        // must be left as the caller had it
        val keyA = ByteArray(20) { 0x0b }
        val keyB = "Jefe".encodeToByteArray()
        val prefed = ByteArray(77) { 1 }
        val shared = PureSha256().apply { update(prefed) }
        val macA = PureHmac(keyA, shared)
        val macB = PureHmac(keyB, shared)
        shared.update(ByteArray(500) { 2 })

        val expectedA = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7" // RFC 4231 case 1
        val expectedB = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843" // RFC 4231 case 2
        assertEquals(expectedA, macA.mac("Hi There".encodeToByteArray()).toHexString())
        assertEquals(expectedB, macB.mac("what do ya want for nothing?".encodeToByteArray()).toHexString())
        assertEquals(expectedA, macA.mac("Hi There".encodeToByteArray()).toHexString())

        val expectedShared = hashFunction(RUNTIME, "sha256").apply {
            update(prefed)
            update(ByteArray(500) { 2 })
        }.digest()
        assertContentEquals(expectedShared, shared.digest())
    }

    @Test
    fun testMacOfPrefix() {
        val hmac = pureHmacFor("sha256", ByteArray(32) { 7 })
        val message = ByteArray(100) { it.toByte() }
        assertContentEquals(hmac.mac(message.copyOf(33)), hmac.mac(message, 33))
    }
}

class MerkleDamgardHashScratchTest {
    @Test
    fun testResetAndWipeScratchClearTheSchedule() {
        for (algorithm in blockHashes) {
            val hash = pureMerkleDamgardHashSupplier(algorithm)()
            hash.update(ByteArray(128) { (it + 1).toByte() })
            if (hash.scratch.isEmpty()) continue // this platform's block function keeps the words in locals
            assertTrue(hash.scratch.any { it != 0 }, "$algorithm: the test needs a schedule to clear")
            hash.wipeScratch()
            assertTrue(hash.scratch.all { it == 0 }, "$algorithm: wipeScratch")

            hash.update(ByteArray(64) { 3 })
            hash.reset()
            assertTrue(hash.scratch.all { it == 0 }, "$algorithm: reset")
        }
    }

    @Test
    fun testWipeScratchKeepsTheHashState() {
        for (algorithm in blockHashes) {
            val prefix = ByteArray(100) { it.toByte() }
            val hash = pureMerkleDamgardHashSupplier(algorithm)().apply { update(prefix) }
            hash.wipeScratch()
            assertContentEquals(hashFunction(RUNTIME, algorithm).apply { update(prefix) }.digest(), hash.digest(), algorithm)
        }
    }
}

class MerkleDamgardHashRestoreTest {
    @Test
    fun testRestoreFromOtherTypeLeavesStateUntouched() {
        val prefix = ByteArray(70) { it.toByte() }
        val suffix = ByteArray(30) { (it + 3).toByte() }
        val sha = PureSha256().apply { update(prefix) }
        val md5 = PureMd5().apply { update(ByteArray(13)) }
        assertFails { sha.restoreFrom(md5) }
        sha.update(suffix)
        val expected = hashFunction(RUNTIME, "sha256").apply {
            update(prefix)
            update(suffix)
        }.digest()
        assertContentEquals(expected, sha.digest())
    }
}
