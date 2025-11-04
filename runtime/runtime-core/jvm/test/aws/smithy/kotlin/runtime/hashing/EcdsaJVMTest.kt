/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.security.*
import java.security.interfaces.*
import java.security.spec.*
import kotlin.test.Test

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

        val rawSignature = ecdsaSecp256r1(privateKey, message, EcdsaSignatureType.RAW_RS)

        assertTrue(rawSignature.isNotEmpty())
        assertEquals(64, rawSignature.size) // Raw signature should be exactly 64 bytes (32 bytes r + 32 bytes s)
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

        val rawSignature1 = ecdsaSecp256r1(privateKey, message1, EcdsaSignatureType.RAW_RS)
        val rawSignature2 = ecdsaSecp256r1(privateKey, message2, EcdsaSignatureType.RAW_RS)

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

        val rawSignature = ecdsaSecp256r1(privateKey, message, EcdsaSignatureType.RAW_RS)
        assertTrue(rawSignature.isNotEmpty())
    }

    @Test
    fun testLargeMessage() {
        val privateKey = generateValidPrivateKey()
        val largeMessage = ByteArray(1000000) { it.toByte() }

        val signature = ecdsaSecp256r1(privateKey, largeMessage)
        assertTrue(signature.isNotEmpty())

        val rawSignature = ecdsaSecp256r1(privateKey, largeMessage, EcdsaSignatureType.RAW_RS)
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
}
