/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning.standard

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAlgorithm
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningResult
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.time.TimestampFormat

/** The standard implementation of [AwsSigner] */
val StandardAwsSigner: AwsSigner = StandardAwsSignerImpl()

internal class StandardAwsSignerImpl(
    private val canonicalizer: Canonicalizer = Canonicalizer.Default,
    private val signatureCalculator: SignatureCalculator = SignatureCalculator.Default,
    private val requestMutator: RequestMutator = RequestMutator.Default,
) : AwsSigner {
    override suspend fun sign(request: HttpRequest, config: AwsSigningConfig): AwsSigningResult<HttpRequest> {
        // TODO implement SigV4a
        require(config.algorithm == AwsSigningAlgorithm.SIGV4) { "Non-SigV4 signing algorithms are not supported" }

        val credentials = config.credentialsProvider.getCredentials()

        val canonical = canonicalizer.canonicalRequest(request, config, credentials)

        val stringToSign = signatureCalculator.stringToSign(canonical.requestString, config)
        val signingKey = signatureCalculator.signingKey(config, credentials)
        val signature = signatureCalculator.calculate(signingKey, stringToSign)

        val signedRequest = requestMutator.appendAuth(config, canonical, credentials, signature)

        return AwsSigningResult(signedRequest, signature.encodeToByteArray())
    }

    override suspend fun signChunk(
        chunkBody: ByteArray,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit> {
        val stringToSign = signatureCalculator.chunkStringToSign(chunkBody, prevSignature, config)

        val credentials = config.credentialsProvider.getCredentials()
        val signingKey = signatureCalculator.signingKey(config, credentials)
        val signature = signatureCalculator.calculate(signingKey, stringToSign)

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
