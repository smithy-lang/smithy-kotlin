/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.text.encoding.decodeHexBytes
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.time.epochMilliseconds

/**
 * An object that can calculate signatures based on canonical requests.
 */
internal interface SignatureCalculator {
    companion object {
        /**
         * The SigV4 implementation of [SignatureCalculator].
         */
        val SigV4 = SigV4SignatureCalculator()

        /**
         * The SigV4a implementation of [SignatureCalculator].
         */
        val SigV4a = SigV4aSignatureCalculator()
    }

    /**
     * Calculates a signature based on a signing key and a string to sign.
     * @param signingKey The signing key as a byte array (returned from [signingKey])
     * @param stringToSign The string to sign (returned from [stringToSign], [chunkStringToSign], or [chunkTrailerStringToSign])
     * @return The signature for this request as a hex string
     */
    fun calculate(signingKey: ByteArray, stringToSign: String): String

    /**
     * Constructs a string to sign for a chunk
     * @param chunkBody The byte contents of the chunk's body
     * @param prevSignature The signature of the previous chunk. If this is the first chunk, use the seed signature.
     * @param config The signing configuration to use
     * @return A multiline string to sign
     */
    fun chunkStringToSign(chunkBody: ByteArray, prevSignature: ByteArray, config: AwsSigningConfig): String

    /**
     * Derives a signing key
     * @param config The signing configuration to use
     * @return The signing key as a byte array
     */
    fun signingKey(config: AwsSigningConfig): ByteArray

    /**
     * Constructs a string to sign for a request
     * @param canonicalRequest The canonical request string (returned from [Canonicalizer.canonicalRequest])
     * @param config The signing configuration to use
     * @return A multiline string to sign
     */
    fun stringToSign(canonicalRequest: String, config: AwsSigningConfig): String

    /**
     * Constructs a string to sign for a chunk trailer
     * @param trailingHeaders The canonicalized trailing headers
     * @param prevSignature The signature of the previous chunk. In most cases, this is the signature of the final data chunk.
     * @param config The signing configuration to use
     * @return A multiline string to sign
     */
    fun chunkTrailerStringToSign(trailingHeaders: ByteArray, prevSignature: ByteArray, config: AwsSigningConfig): String
}

/**
 * Common signature implementation used for SigV4 and SigV4a, primarily for forming the strings-to-sign which don't differ
 * between the two signing algorithms (besides their names).
 */
internal abstract class SigV4xSignatureCalculator(
    val algorithm: AwsSigningAlgorithm,
    open val sha256Provider: HashSupplier = ::Sha256,
) : SignatureCalculator {
    init {
        check (algorithm == AwsSigningAlgorithm.SIGV4 || algorithm == AwsSigningAlgorithm.SIGV4_ASYMMETRIC) {
            "This class should only be used for the ${AwsSigningAlgorithm.SIGV4} or ${AwsSigningAlgorithm.SIGV4_ASYMMETRIC} algorithms, got $algorithm"
        }
    }

    override fun stringToSign(canonicalRequest: String, config: AwsSigningConfig): String = buildString {
        appendLine(algorithm.signingName)
        appendLine(config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED))
        appendLine(config.credentialScope)
        append(canonicalRequest.encodeToByteArray().hash(sha256Provider).encodeToHex())
    }

    override fun chunkStringToSign(chunkBody: ByteArray, prevSignature: ByteArray, config: AwsSigningConfig): String = buildString {
        appendLine("${algorithm.signingName}-PAYLOAD")
        appendLine(config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED))
        appendLine(config.credentialScope)
        appendLine(prevSignature.decodeToString()) // Should already be a byte array of ASCII hex chars

        val nonSignatureHeadersHash = when (config.signatureType) {
            AwsSignatureType.HTTP_REQUEST_EVENT -> eventStreamNonSignatureHeaders(config.signingDate)
            else -> HashSpecification.EmptyBody.hash
        }

        appendLine(nonSignatureHeadersHash)
        append(chunkBody.hash(sha256Provider).encodeToHex())
    }

    override fun chunkTrailerStringToSign(trailingHeaders: ByteArray, prevSignature: ByteArray, config: AwsSigningConfig): String =
        buildString {
            appendLine("${algorithm.signingName}-TRAILER")
            appendLine(config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED))
            appendLine(config.credentialScope)
            appendLine(prevSignature.decodeToString())
            append(trailingHeaders.hash(sha256Provider).encodeToHex())
        }
}

internal val AwsSigningAlgorithm.signingName: String
    get() = when (this) {
        AwsSigningAlgorithm.SIGV4 -> "AWS4-HMAC-SHA256"
        AwsSigningAlgorithm.SIGV4_ASYMMETRIC -> "AWS4-ECDSA-P256-SHA256"
    }


internal class SigV4SignatureCalculator(override val sha256Provider: HashSupplier = ::Sha256) : SigV4xSignatureCalculator(AwsSigningAlgorithm.SIGV4, sha256Provider) {
    override fun calculate(signingKey: ByteArray, stringToSign: String): String =
        hmac(signingKey, stringToSign.encodeToByteArray(), sha256Provider).encodeToHex()

    override fun signingKey(config: AwsSigningConfig): ByteArray {
        fun hmac(key: ByteArray, message: String) = hmac(key, message.encodeToByteArray(), sha256Provider)

        val initialKey = ("AWS4" + config.credentials.secretAccessKey).encodeToByteArray()
        val kDate = hmac(initialKey, config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED_DATE))
        val kRegion = hmac(kDate, config.region)
        val kService = hmac(kRegion, config.service)
        return hmac(kService, "aws4_request")
    }
}

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
        val nMinusTwo = BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC63254f".decodeHexBytes())

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

private const val HEADER_TIMESTAMP_TYPE: Byte = 8

/**
 * Return the sha256 hex representation of the encoded event stream date header
 *
 * ```
 * sha256Hex( Header(":date", HeaderValue::Timestamp(date)).encodeToByteArray() )
 * ```
 *
 * NOTE: This duplicates parts of the event stream encoding implementation here to avoid a direct dependency.
 * Should this become more involved than encoding a single date header we should reconsider this choice.
 *
 * see [Event Stream implementation](https://github.com/awslabs/aws-sdk-kotlin/blob/v0.16.4-beta/aws-runtime/protocols/aws-event-stream/common/src/aws/sdk/kotlin/runtime/protocol/eventstream/Header.kt#L51)
 */
private fun eventStreamNonSignatureHeaders(date: Instant): String {
    val bytes = ByteArray(15)
    // encode header name
    val name = ":date".encodeToByteArray()
    var offset = 0
    bytes[offset++] = name.size.toByte()
    name.copyInto(bytes, destinationOffset = offset)
    offset += name.size

    // encode header value
    bytes[offset++] = HEADER_TIMESTAMP_TYPE
    writeLongBE(bytes, offset, date.epochMilliseconds)
    return bytes.sha256().encodeToHex()
}

private fun writeLongBE(dest: ByteArray, offset: Int, x: Long) {
    var idx = offset
    for (i in 7 downTo 0) {
        dest[idx++] = ((x ushr (i * 8)) and 0xff).toByte()
    }
}
