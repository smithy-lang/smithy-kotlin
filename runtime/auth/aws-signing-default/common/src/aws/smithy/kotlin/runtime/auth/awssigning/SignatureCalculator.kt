/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

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
