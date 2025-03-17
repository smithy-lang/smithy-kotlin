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
import aws.smithy.kotlin.runtime.hashing.ecdsaSecp256r1
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

// N value from NIST P-256 curve, minus two.
internal val N_MINUS_TWO = "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC63254F".decodeHexBytes().toPositiveBigInteger()

/**
 * A [SignatureCalculator] for the SigV4a ("AWS4-ECDSA-P256-SHA256") algorithm.
 * @param sha256Provider the [HashSupplier] to use for computing SHA-256 hashes
 */
internal class SigV4aSignatureCalculator(override val sha256Provider: HashSupplier = ::Sha256) : BaseSigV4SignatureCalculator(AwsSigningAlgorithm.SIGV4_ASYMMETRIC, sha256Provider) {
    private val privateKeyCache = ReadThroughCache<Credentials, ByteArray>(
        minimumSweepPeriod = 1.hours, // note: Sweeps are effectively a no-op because expiration is [Instant.MAX_VALUE]
    )

    override fun calculate(signingKey: ByteArray, stringToSign: String): String = ecdsaSecp256r1(signingKey, stringToSign.encodeToByteArray()).encodeToHex()

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

            val inputKey = ("AWS4A" + config.credentials.secretAccessKey).encodeToByteArray()

            do {
                val k0 = hmac(inputKey, fixedInputString(config.credentials.accessKeyId, counter), sha256Provider)

                val c = k0.toPositiveBigInteger()
                privateKey = (c + BigInteger("1")).toByteArray()

                if (counter == MAX_KDF_COUNTER_ITERATIONS && c > N_MINUS_TWO) {
                    throw IllegalStateException("Counter exceeded maximum length")
                } else {
                    counter++
                }
            } while (c > N_MINUS_TWO)

            ExpiringValue<ByteArray>(privateKey, Instant.MAX_VALUE)
        }
    }

    /**
     * Forms the fixed input string used for ECDSA private key derivation
     * The final output looks like:
     * 0x00000001 || "AWS4-ECDSA-P256-SHA256" || 0x00 || AccessKeyId || counter || 0x00000100
     */
    private fun fixedInputString(accessKeyId: String, counter: Byte): ByteArray =
        byteArrayOf(0x00, 0x00, 0x00, 0x01) +
            AwsSigningAlgorithm.SIGV4_ASYMMETRIC.signingName.encodeToByteArray() +
            byteArrayOf(0x00) +
            accessKeyId.encodeToByteArray() +
            counter +
            byteArrayOf(0x00, 0x00, 0x01, 0x00)
}

// Convert [this] [ByteArray] to a positive [BigInteger] by prepending 0x00.
private fun ByteArray.toPositiveBigInteger() = BigInteger(byteArrayOf(0x00) + this)
