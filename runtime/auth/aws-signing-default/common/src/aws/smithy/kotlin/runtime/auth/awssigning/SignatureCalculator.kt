/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.time.TimestampFormat
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
     * @param stringToSign The string to sign (returned from [stringToSign] or [chunkStringToSign])
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
     * @param credentials Retrieved credentials to use
     * @return The signing key as a byte array
     */
    fun signingKey(config: AwsSigningConfig, credentials: Credentials): ByteArray

    /**
     * Constructs a string to sign for a request
     * @param canonicalRequest The canonical request string (returned from [Canonicalizer.canonicalRequest])
     * @param config The signing configuration to use
     * @return A multiline string to sign
     */
    fun stringToSign(canonicalRequest: String, config: AwsSigningConfig): String
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
            appendLine(HashSpecification.EmptyBody.hash)
            append(chunkBody.hash(sha256Provider).encodeToHex())
        }

    override fun signingKey(config: AwsSigningConfig, credentials: Credentials): ByteArray {
        fun hmac(key: ByteArray, message: String) = hmac(key, message.encodeToByteArray(), sha256Provider)

        val initialKey = ("AWS4" + credentials.secretAccessKey).encodeToByteArray()
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
