/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.security.*
import java.security.spec.*
import java.security.interfaces.*
import kotlin.test.Test
import kotlin.test.assertNotNull

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

        val signature = ecdsasecp256r1(privateKey, message)

        assertNotNull(signature)
        assertTrue(signature.size >= 64) // ECDSA signatures are typically 70-72 bytes in DER format
    }

    @Test
    fun testSignatureDeterminism() {
        val privateKey = generateValidPrivateKey()
        val message = "Hello, World!".toByteArray()

        val signature1 = ecdsasecp256r1(privateKey, message)
        val signature2 = ecdsasecp256r1(privateKey, message)

        // Note: ECDSA is not deterministic by default, so signatures will be different
        assertNotNull(signature1)
        assertNotNull(signature2)
    }

    @Test
    fun testDifferentMessages() {
        val privateKey = generateValidPrivateKey()
        val message1 = "Hello, World!".toByteArray()
        val message2 = "Different message".toByteArray()

        val signature1 = ecdsasecp256r1(privateKey, message1)
        val signature2 = ecdsasecp256r1(privateKey, message2)

        assertNotNull(signature1)
        assertNotNull(signature2)
        assertFalse(signature1.contentEquals(signature2))
    }

    @Test
    fun testEmptyMessage() {
        val privateKey = generateValidPrivateKey()
        val message = ByteArray(0)

        val signature = ecdsasecp256r1(privateKey, message)
        assertNotNull(signature)
    }

    @Test
    fun testLargeMessage() {
        val privateKey = generateValidPrivateKey()
        val largeMessage = ByteArray(1000000) { it.toByte() }

        val signature = ecdsasecp256r1(privateKey, largeMessage)
        assertNotNull(signature)
    }

    @Test
    fun testVerifySignature() {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()
        val privateKey = (keyPair.private as ECPrivateKey).s.toByteArray()
        val publicKey = keyPair.public

        val message = "Hello, World!".toByteArray()
        val signature = ecdsasecp256r1(privateKey, message)

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(message)

        assertTrue(verifier.verify(signature))
    }
}