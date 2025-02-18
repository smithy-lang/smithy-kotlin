/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.hashing.HashSupplier
import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.hashing.ecdsasecp256r1
import aws.smithy.kotlin.runtime.hashing.hmac
import aws.smithy.kotlin.runtime.text.encoding.decodeHexBytes
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex

/**
 * The maximum number of iterations to attempt private key derivation using KDF in counter mode
 * Taken from CRT: https://github.com/awslabs/aws-c-auth/blob/e8360a65e0f3337d4ac827945e00c3b55a641a5f/source/key_derivation.c#L22
 */
internal const val MAX_KDF_COUNTER_ITERATIONS = 254.toByte()

internal class SigV4aSignatureCalculator(override val sha256Provider: HashSupplier = ::Sha256) : SigV4xSignatureCalculator(AwsSigningAlgorithm.SIGV4_ASYMMETRIC, sha256Provider) {
    override fun calculate(signingKey: ByteArray, stringToSign: String): String = ecdsasecp256r1(signingKey, stringToSign.encodeToByteArray()).encodeToHex()

    // See https://github.com/awslabs/aws-c-auth/blob/e8360a65e0f3337d4ac827945e00c3b55a641a5f/source/key_derivation.c#L70 for more details of derivation process
    override fun signingKey(config: AwsSigningConfig): ByteArray {
        var counter: Byte = 1
        var privateKey: ByteArray

        // N value from NIST P-256 curve, minus two.
        val nMinusTwo = BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC63254F".decodeHexBytes())

        // FIXME Public docs say secret access key needs to be Base64 encoded, that's not right.
        // (or maybe it's already base64-encoded, and they are just repeating it)
        val inputKey = ("AWS4A" + config.credentials.secretAccessKey).encodeToByteArray()

        do {
            // 1.2: Compute K0
            val k0 = hmac(inputKey, fixedInputString(config.credentials.accessKeyId, counter), sha256Provider)

            // 2: Compute the ECC key pair
            val c = BigInteger(k0)

            privateKey = (c + BigInteger("1")).toByteArray()

            if (counter == MAX_KDF_COUNTER_ITERATIONS && c > nMinusTwo) {
                throw IllegalStateException("Counter exceeded maximum length")
            } else {
                counter++
            }
        } while (c > nMinusTwo)

        return privateKey
    }

    /**
     * Computes the fixed input string used for ECDSA private key derivation
     * The final output looks like:
     * 0x00000001 || "AWS4-ECDSA-P256-SHA256" || 0x00 || AccessKeyId || counter || 0x00000100
     */
    private fun fixedInputString(accessKeyId: String, counter: Byte): ByteArray =
        byteArrayOf(0x00, 0x00, 0x00, 0x01) + // FIXME CRT implementation (4 bytes) and internal docs (1 byte) conflict.
                "AWS4-ECDSA-P256-SHA256".encodeToByteArray() +
                byteArrayOf(0x00) +
                accessKeyId.encodeToByteArray() +
                counter +
                byteArrayOf(0x00, 0x00, 0x01, 0x00) // FIXME CRT implementation (4 bytes) and internal docs (2 bytes) conflict.
}