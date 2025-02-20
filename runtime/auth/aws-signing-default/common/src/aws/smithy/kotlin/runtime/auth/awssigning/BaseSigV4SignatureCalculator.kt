/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.hashing.HashSupplier
import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.hashing.hash
import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.time.epochMilliseconds

/**
 * Common signature implementation used for SigV4 and SigV4a, primarily for forming the strings-to-sign which don't differ
 * between the two signing algorithms (besides their names).
 */
internal abstract class BaseSigV4SignatureCalculator(
    val algorithm: AwsSigningAlgorithm,
    open val sha256Provider: HashSupplier = ::Sha256,
) : SignatureCalculator {
    init {
        check(algorithm == AwsSigningAlgorithm.SIGV4 || algorithm == AwsSigningAlgorithm.SIGV4_ASYMMETRIC) {
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
