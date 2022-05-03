/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.encodeToHex
import kotlin.test.Test
import kotlin.test.assertEquals

// Test data adapted from https://en.wikipedia.org/wiki/HMAC#Examples

private const val PANGRAM = "The quick brown fox jumps over the lazy dog"

class HmacTest {
    @Test
    fun testMd5() {
        assertEquals("80070713463e7749b90c2dc24911e275", hmacTest("key", PANGRAM, ::Md5))
    }

    @Test
    fun testSha1() {
        assertEquals("de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9", hmacTest("key", PANGRAM, ::Sha1))
    }

    @Test
    fun testSha256() {
        assertEquals(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            hmacTest("key", PANGRAM, ::Sha256),
        )

        assertEquals(
            "5597b93a2843078cbb0c920ae41dfe20f1685e10c67e423c11ab91adfc319d12",
            hmacTest(PANGRAM.repeat(2), "message", ::Sha256),
        )
    }
}

private fun hmacTest(key: String, message: String, hmacSupplier: HashSupplier): String =
    hmac(key.encodeToByteArray(), message.encodeToByteArray(), hmacSupplier).encodeToHex()
