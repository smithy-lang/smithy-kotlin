/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.content.BigInteger
import java.security.*
import java.security.spec.*
import java.security.interfaces.*

/**
 * ECDSA on the SECP256R1 curve.
 */
public actual fun ecdsasecp256r1(key: ByteArray, message: ByteArray): ByteArray {
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
    return Signature.getInstance("SHA256withECDSA").apply {
        initSign(privateKey)
        update(message)
    }.sign()
}

private fun BigInteger.toJvm(): java.math.BigInteger = java.math.BigInteger(1, toByteArray())
