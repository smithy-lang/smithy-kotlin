/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import java.security.*
import java.security.interfaces.*
import java.security.spec.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EcdsaJVMTest {
    // Helper function to generate valid test key
    private fun generateValidPrivateKey(): ByteArray {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()
        val privateKey = keyPair.private as ECPrivateKey
        return privateKey.s.toByteArray()
    }

    @Test
    fun testValidSignature() {
        val privateKey = generateValidPrivateKey()
        val message = "Hello, World!".toByteArray()

        val signature = ecdsaSecp256r1(privateKey, message)

        assertTrue(signature.isNotEmpty())
        assertTrue(signature.size >= 64) // ECDSA signatures are typically 70-72 bytes in DER format

        val rawSignature = ecdsaSecp256r1Rs(privateKey, message)
        assertTrue(signature.isNotEmpty())
        assertTrue(rawSignature.size == 64)
    }

    @Test
    fun testDifferentMessages() {
        val privateKey = generateValidPrivateKey()
        val message1 = "Hello, World!".toByteArray()
        val message2 = "Different message".toByteArray()

        val signature1 = ecdsaSecp256r1(privateKey, message1)
        val signature2 = ecdsaSecp256r1(privateKey, message2)

        assertTrue(signature1.isNotEmpty())
        assertTrue(signature2.isNotEmpty())
        assertFalse(signature1.contentEquals(signature2))

        val rawSignature1 = ecdsaSecp256r1Rs(privateKey, message1)
        val rawSignature2 = ecdsaSecp256r1Rs(privateKey, message2)

        assertTrue(rawSignature1.isNotEmpty())
        assertTrue(rawSignature2.isNotEmpty())
        assertFalse(rawSignature1.contentEquals(rawSignature2))
    }

    @Test
    fun testEmptyMessage() {
        val privateKey = generateValidPrivateKey()
        val message = ByteArray(0)

        val signature = ecdsaSecp256r1(privateKey, message)
        assertTrue(signature.isNotEmpty())

        val rawSignature = ecdsaSecp256r1Rs(privateKey, message)
        assertTrue(rawSignature.isNotEmpty())
    }

    @Test
    fun testLargeMessage() {
        val privateKey = generateValidPrivateKey()
        val largeMessage = ByteArray(1000000) { it.toByte() }

        val signature = ecdsaSecp256r1(privateKey, largeMessage)
        assertTrue(signature.isNotEmpty())

        val rawSignature = ecdsaSecp256r1Rs(privateKey, largeMessage)
        assertTrue(rawSignature.isNotEmpty())
    }

    @Test
    fun testVerifySignature() {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()
        val privateKey = (keyPair.private as ECPrivateKey).s.toByteArray()
        val publicKey = keyPair.public

        val message = "Hello, World!".toByteArray()
        val signature = ecdsaSecp256r1(privateKey, message)

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(message)

        assertTrue(verifier.verify(signature))
    }

    // Key pair and messages from RFC 6979 appendix A.2.5. Our signer uses random nonces, so the RFC's exact signatures
    // can't be reproduced; instead verify against the RFC's known public key.
    @Test
    fun testVerifyRfc6979Vectors() {
        val privateKey = java.math.BigInteger("c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721", 16)
            .toByteArray()
        val publicKey = rfc6979PublicKey()

        for (message in listOf("sample", "test")) {
            val signature = ecdsaSecp256r1(privateKey, message.toByteArray())
            val verifier = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(message.toByteArray())
            }
            assertTrue(verifier.verify(signature), "signature for \"$message\" did not verify")
        }
    }

    private fun rfc6979PublicKey(): PublicKey {
        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(
            java.math.BigInteger("60fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb6", 16),
            java.math.BigInteger("7903fe1008b8bc99a41ae9e95628bc64f2f1b20c2d7e9f5177a3c294d4462299", 16),
        )
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
    }

    // SHA256withECDSAinP1363Format algorithm was introduced in Java 9, skip on Java 8
    @Test
    fun testVerifyRawSignature() {
        val verifier = runCatching { Signature.getInstance("SHA256withECDSAinP1363Format") }.getOrElse { return }

        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()
        val privateKey = (keyPair.private as ECPrivateKey).s.toByteArray()
        val publicKey = keyPair.public

        val message = "Hello, World!".toByteArray()
        val signature = ecdsaSecp256r1Rs(privateKey, message)

        verifier.initVerify(publicKey)
        verifier.update(message)

        assertTrue(verifier.verify(signature))
    }
}
