/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.operation.getLogger
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.time.TimestampFormat
import kotlin.coroutines.coroutineContext

/** The default implementation of [AwsSigner] */
public val DefaultAwsSigner: AwsSigner = DefaultAwsSignerImpl()

internal class DefaultAwsSignerImpl(
    private val canonicalizer: Canonicalizer = Canonicalizer.Default,
    private val signatureCalculator: SignatureCalculator = SignatureCalculator.Default,
    private val requestMutator: RequestMutator = RequestMutator.Default,
) : AwsSigner {
    override suspend fun sign(request: HttpRequest, config: AwsSigningConfig): AwsSigningResult<HttpRequest> {
        val logger = coroutineContext.getLogger<DefaultAwsSignerImpl>()

        // TODO implement SigV4a
        require(config.algorithm == AwsSigningAlgorithm.SIGV4) { "${config.algorithm} support is not yet implemented" }

        val credentials = config.credentialsProvider.getCredentials()

        val canonical = canonicalizer.canonicalRequest(request, config, credentials)
        logger.trace { "Canonical request:\n${canonical.requestString}" }

        val stringToSign = signatureCalculator.stringToSign(canonical.requestString, config)
        logger.trace { "String to sign:\n$stringToSign" }

        val signingKey = signatureCalculator.signingKey(config, credentials)

        val signature = signatureCalculator.calculate(signingKey, stringToSign)
        logger.debug { "Calculated signature: $signature" }

        val signedRequest = requestMutator.appendAuth(config, canonical, credentials, signature)

        return AwsSigningResult(signedRequest, signature.encodeToByteArray())
    }

    override suspend fun signChunk(
        chunkBody: ByteArray,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit> {
        val logger = coroutineContext.getLogger<DefaultAwsSignerImpl>()

        val stringToSign = signatureCalculator.chunkStringToSign(chunkBody, prevSignature, config)
        logger.trace { "Chunk string to sign:\n$stringToSign" }

        val credentials = config.credentialsProvider.getCredentials()
        val signingKey = signatureCalculator.signingKey(config, credentials)

        val signature = signatureCalculator.calculate(signingKey, stringToSign)
        logger.debug { "Calculated chunk signature: $signature" }

        return AwsSigningResult(Unit, signature.encodeToByteArray())
    }

    override suspend fun signChunkTrailer(
        trailingHeaders: Headers,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit> {
        val logger = coroutineContext.getLogger<DefaultAwsSignerImpl>()

        // canonicalize the headers
        val trailingHeadersBytes = trailingHeaders.entries().sortedBy { e -> e.key.lowercase() }
            .map { e ->
                buildString {
                    append(e.key.lowercase())
                    append(":")
                    append(e.value.joinToString(",") { v -> v.trim() })
                    append("\n")
                }.encodeToByteArray()
            }.reduce { acc, bytes -> acc + bytes }

        val stringToSign = signatureCalculator.chunkTrailerStringToSign(trailingHeadersBytes, prevSignature, config)
        logger.trace { "Chunk trailer string to sign:\n$stringToSign" }

        val credentials = config.credentialsProvider.getCredentials()
        val signingKey = signatureCalculator.signingKey(config, credentials)

        val signature = signatureCalculator.calculate(signingKey, stringToSign)
        logger.debug { "Calculated chunk signature: $signature" }

        return AwsSigningResult(Unit, signature.encodeToByteArray())
    }
}

/** The name of the SigV4 algorithm. */
internal const val ALGORITHM_NAME = "AWS4-HMAC-SHA256"

/**
 * Formats a credential scope consisting of a signing date, region, service, and a signature type
 */
internal val AwsSigningConfig.credentialScope: String
    get() {
        val signingDate = signingDate.format(TimestampFormat.ISO_8601_CONDENSED_DATE)
        return "$signingDate/$region/$service/aws4_request"
    }

/**
 * Formats the value for a credential header/parameter
 */
internal fun credentialValue(config: AwsSigningConfig, credentials: Credentials): String =
    "${credentials.accessKeyId}/${config.credentialScope}"
