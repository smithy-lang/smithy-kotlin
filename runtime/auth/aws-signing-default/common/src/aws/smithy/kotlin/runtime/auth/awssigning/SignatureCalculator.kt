/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import aws.smithy.kotlin.runtime.util.encodeToHex

/**
 * An object that can calculate signatures based on canonical requests.
 */
internal interface SignatureCalculator {
    companion object {
        /**
         * The default implementation of [SignatureCalculator].
         */
        val Default = DefaultSignatureCalculator()
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

internal class DefaultSignatureCalculator(private val sha256Provider: HashSupplier = ::Sha256) : SignatureCalculator {
    override fun calculate(signingKey: ByteArray, stringToSign: String): String =
        hmac(signingKey, stringToSign.encodeToByteArray(), sha256Provider).encodeToHex()

    override fun chunkStringToSign(chunkBody: ByteArray, prevSignature: ByteArray, config: AwsSigningConfig): String =
        buildString {
            appendLine("AWS4-HMAC-SHA256-PAYLOAD")
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
            appendLine("AWS4-HMAC-SHA256-TRAILER")
            appendLine(config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED))
            appendLine(config.credentialScope)
            appendLine(prevSignature.decodeToString())
            append(trailingHeaders.hash(sha256Provider).encodeToHex())
        }

    override fun signingKey(config: AwsSigningConfig): ByteArray {
        fun hmac(key: ByteArray, message: String) = hmac(key, message.encodeToByteArray(), sha256Provider)

        val initialKey = ("AWS4" + config.credentials.secretAccessKey).encodeToByteArray()
        val kDate = hmac(initialKey, config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED_DATE))
        val kRegion = hmac(kDate, config.region)
        val kService = hmac(kRegion, config.service)
        return hmac(kService, "aws4_request")
    }

    override fun stringToSign(canonicalRequest: String, config: AwsSigningConfig): String =
        buildString {
            appendLine("AWS4-HMAC-SHA256")
            appendLine(config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED))
            appendLine(config.credentialScope)
            append(canonicalRequest.encodeToByteArray().hash(sha256Provider).encodeToHex())
        }
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
