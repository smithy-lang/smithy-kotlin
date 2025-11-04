/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.content.BigInteger
import java.security.*
import java.security.interfaces.*
import java.security.spec.*

/**
 * ECDSA on the SECP256R1 curve.
 */
public actual fun ecdsaSecp256r1(
    key: ByteArray,
    message: ByteArray,
    signatureType: EcdsaSignatureType,
): ByteArray {
    // Convert private key to BigInteger
    val d = BigInteger(key)

    // Create key pair generator to get curve parameters
    val keyGen = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }
    val params = (keyGen.generateKeyPair().private as ECPrivateKey).params

    // Create private key directly from the provided key bytes
    val privateKeySpec = ECPrivateKeySpec(d.toJvm(), params)
    val keyFactory = KeyFactory.getInstance("EC")
    val privateKey = keyFactory.generatePrivate(privateKeySpec)

    // Sign the message
    val derSignature = Signature.getInstance("SHA256withECDSA").apply {
        initSign(privateKey)
        update(message)
    }.sign()

    return when (signatureType) {
        EcdsaSignatureType.ASN1_DER -> derSignature
        EcdsaSignatureType.RAW_RS -> parseDerSignature(derSignature)
    }
}

private fun BigInteger.toJvm(): java.math.BigInteger = java.math.BigInteger(1, toByteArray())

/**
 * Parses an ASN.1 DER encoded ECDSA signature and converts it to raw r||s format.
 */
private fun parseDerSignature(derSignature: ByteArray): ByteArray {
    var index = 2 // Skip SEQUENCE tag and length

    // Read r
    index++ // Skip INTEGER tag
    val rLength = derSignature[index++].toInt() and 0xFF
    val r = derSignature.sliceArray(index until index + rLength)
    index += rLength

    // Read s
    index++ // Skip INTEGER tag
    val sLength = derSignature[index++].toInt() and 0xFF
    val s = derSignature.sliceArray(index until index + sLength)

    // Remove leading zero bytes and pad to 32 bytes
    val rFixed = r.dropWhile { it == 0.toByte() }.toByteArray()
    val sFixed = s.dropWhile { it == 0.toByte() }.toByteArray()

    val rPadded = if (rFixed.size < 32) ByteArray(32 - rFixed.size) + rFixed else rFixed
    val sPadded = if (sFixed.size < 32) ByteArray(32 - sFixed.size) + sFixed else sFixed

    return rPadded + sPadded
}
