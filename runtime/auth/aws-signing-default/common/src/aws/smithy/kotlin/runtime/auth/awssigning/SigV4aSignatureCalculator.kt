/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.collections.ReadThroughCache
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.hashing.HashSupplier
import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.hashing.ecdsasecp256r1
import aws.smithy.kotlin.runtime.hashing.hmac
import aws.smithy.kotlin.runtime.text.encoding.decodeHexBytes
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.ExpiringValue
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.hours

/**
 * The maximum number of iterations to attempt private key derivation using KDF in counter mode
 * Taken from CRT: https://github.com/awslabs/aws-c-auth/blob/e8360a65e0f3337d4ac827945e00c3b55a641a5f/source/key_derivation.c#L22
 */
internal const val MAX_KDF_COUNTER_ITERATIONS = 254.toByte()

/**
 * A [SignatureCalculator] for the SigV4a ("AWS4-ECDSA-P256-SHA256") algorithm.
 * @param sha256Provider the [HashSupplier] to use for computing SHA-256 hashes
 */
internal class SigV4aSignatureCalculator(override val sha256Provider: HashSupplier = ::Sha256) : SigV4xSignatureCalculator(AwsSigningAlgorithm.SIGV4_ASYMMETRIC, sha256Provider) {
    private val privateKeyCache = ReadThroughCache<Credentials, ByteArray>(
        minimumSweepPeriod = 1.hours, // note: Sweeps are effectively a no-op because expiration is [Instant.MAX_VALUE]
    )

    override fun calculate(signingKey: ByteArray, stringToSign: String): String = ecdsasecp256r1(signingKey, stringToSign.encodeToByteArray()).encodeToHex()

    /**
     * Retrieve a signing key based on the signing credentials. If not cached, the key will be derived using a counter-based key derivation function (KDF)
     * as specified in NIST SP 800-108.
     *
     * See https://github.com/awslabs/aws-c-auth/blob/e8360a65e0f3337d4ac827945e00c3b55a641a5f/source/key_derivation.c#L70 and
     * https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-create-signed-request.html#derive-signing-key-sigv4a for
     * more information on the derivation process.
     */
    override fun signingKey(config: AwsSigningConfig): ByteArray = runBlocking {
        privateKeyCache.get(config.credentials) {
            var counter: Byte = 1
            var privateKey: ByteArray

            // N value from NIST P-256 curve, minus two.
            val nMinusTwo = "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC63254F".decodeHexBytes().toPositiveBigInteger()

            val inputKey = ("AWS4A" + config.credentials.secretAccessKey).encodeToByteArray()

            do {
                val k0 = hmac(inputKey, fixedInputString(config.credentials.accessKeyId, counter), sha256Provider)

                val c = k0.toPositiveBigInteger()
                privateKey = (c + BigInteger("1")).toByteArray()

                if (counter == MAX_KDF_COUNTER_ITERATIONS && c > nMinusTwo) {
                    throw IllegalStateException("Counter exceeded maximum length")
                } else {
                    counter++
                }
            } while (c > nMinusTwo)

            ExpiringValue<ByteArray>(privateKey, Instant.MAX_VALUE)
        }
    }

    /**
     * Forms the fixed input string used for ECDS private key derivation
     * The final output looks like:
     * 0x00000001 || "AWS4-ECDSA-P256-SHA256" || 0x00 || AccessKeyId || counter || 0x00000100
     */
    private fun fixedInputString(accessKeyId: String, counter: Byte): ByteArray =
        byteArrayOf(0x00, 0x00, 0x00, 0x01) +
            "AWS4-ECDSA-P256-SHA256".encodeToByteArray() +
            byteArrayOf(0x00) +
            accessKeyId.encodeToByteArray() +
            counter +
            byteArrayOf(0x00, 0x00, 0x01, 0x00)
}

// Convert [this] [ByteArray] to a positive [BigInteger]
private fun ByteArray.toPositiveBigInteger(): BigInteger = if (isNotEmpty() && (get(0).toInt() and 0x80) != 0) {
    BigInteger(byteArrayOf(0x00) + this) // Prepend 0x00 to ensure positive value
} else {
    BigInteger(this)
}
