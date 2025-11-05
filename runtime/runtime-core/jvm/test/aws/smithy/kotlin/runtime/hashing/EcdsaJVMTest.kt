/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
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

    @Test
    @EnabledForJreRange(min = JRE.JAVA_11)
    fun testVerifyRawSignature() {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()
        val privateKey = (keyPair.private as ECPrivateKey).s.toByteArray()
        val publicKey = keyPair.public

        val message = "Hello, World!".toByteArray()
        val signature = ecdsaSecp256r1Rs(privateKey, message)

        val verifier = Signature.getInstance("SHA256withECDSAinP1363Format")
        verifier.initVerify(publicKey)
        verifier.update(message)

        assertTrue(verifier.verify(signature))
    }
}
