/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

/**
 * ECDSA on the SECP256R1 curve returning ASN.1 DER format.
 */
public expect fun ecdsaSecp256r1(key: ByteArray, message: ByteArray): ByteArray

/**
 * ECDSA on the SECP256R1 curve returning raw r||s format.
 */
public fun ecdsaSecp256r1Rs(key: ByteArray, message: ByteArray): ByteArray {
    val derSignature = ecdsaSecp256r1(key, message)
    return parseDerSignature(derSignature)
}

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
