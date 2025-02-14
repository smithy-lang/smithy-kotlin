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
    // Convert byte array to BigInteger for private key
    val d = BigInteger(key)

    // Get EC parameters for SECP256R1
    val spec = ECGenParameterSpec("secp256r1")

    // Create key pair generator to get curve parameters
    val keyGen = KeyPairGenerator.getInstance("EC")
    keyGen.initialize(spec)
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

//public actual fun ecdsasecp256r1(key: ByteArray, message: ByteArray): ByteArray {
//    val d = BigInteger(key)
//
//    // Get EC curve parameters for SECP256R1
//    val spec = ECGenParameterSpec("secp256r1")
//    val keyFactory = KeyFactory.getInstance("EC")
//
//    // Generate ECPrivateKeySpec
//    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
//    keyPairGenerator.initialize(spec)
//    val keyPair = keyPairGenerator.generateKeyPair()
//    val ecParams = (keyPair.private as ECPrivateKey).params
//
//    // Compute the public key point P = d * G
//    val G = ecParams.generator
//    val publicPoint = ecParams.curve.multiply(G, d)
//
//    // Create EC public key
//    val pubKeySpec = ECPublicKeySpec(publicPoint, ecParams)
//    val publicKey = keyFactory.generatePublic(pubKeySpec) as ECPublicKey
//
//    // Create EC private key
//    val privateKeySpec = ECPrivateKeySpec(d, ecParams)
//    val privateKey = keyFactory.generatePrivate(privateKeySpec) as ECPrivateKey
//
//    // Sign the message
//    return Signature.getInstance("SHA256withECDSA").apply {
//        initSign(privateKey)
//        update(message)
//    }.sign()
//}

///**
// * ECDSA on the SECP256R1 curve.
// */
//public actual fun ecdsasecp256r1(key: ByteArray, message: ByteArray): ByteArray {
//    val d = BigInteger(key)
//
//    // Get SECP256R1 parameters from Java's built-in provider
//    val params = AlgorithmParameters.getInstance("EC").apply {
//        init(ECGenParameterSpec("secp256r1"))
//    }
//    val ecSpec = params.getParameterSpec(ECParameterSpec::class.java)
//
//    // Create private key spec and generate key
//    val keySpec = ECPrivateKeySpec(d.toJvm(), ecSpec)
//    val kf = KeyFactory.getInstance("EC")
//    val privateKey = kf.generatePrivate(keySpec)
//
//    // Sign the message
//    return Signature.getInstance("SHA256withECDSA").apply {
//        initSign(privateKey)
//        update(message)
//    }.sign()
//}

private fun BigInteger.toJvm(): java.math.BigInteger = java.math.BigInteger(1, toByteArray())
