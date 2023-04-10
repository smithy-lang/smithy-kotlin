/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning.crt

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.*
import aws.smithy.kotlin.runtime.crt.toSignableCrtRequest
import aws.smithy.kotlin.runtime.crt.update
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import aws.smithy.kotlin.runtime.tracing.debug
import kotlin.coroutines.coroutineContext
import aws.sdk.kotlin.crt.auth.credentials.Credentials as CrtCredentials
import aws.sdk.kotlin.crt.auth.signing.AwsSignatureType as CrtSignatureType
import aws.sdk.kotlin.crt.auth.signing.AwsSignedBodyHeaderType as CrtSignedBodyHeaderType
import aws.sdk.kotlin.crt.auth.signing.AwsSigner as CrtSigner
import aws.sdk.kotlin.crt.auth.signing.AwsSigningAlgorithm as CrtSigningAlgorithm
import aws.sdk.kotlin.crt.auth.signing.AwsSigningConfig as CrtSigningConfig
import aws.sdk.kotlin.crt.http.Headers as CrtHeaders

public object CrtAwsSigner : AwsSigner {
    override suspend fun sign(request: HttpRequest, config: AwsSigningConfig): AwsSigningResult<HttpRequest> {
        val isUnsigned = config.hashSpecification is HashSpecification.UnsignedPayload
        val isAwsChunked = request.headers.contains("Content-Encoding", "aws-chunked")
        val crtRequest = request.toSignableCrtRequest(isUnsigned, isAwsChunked)
        val crtConfig = config.toCrtSigningConfig()

        val crtResult = CrtSigner.sign(crtRequest, crtConfig)
        coroutineContext.debug<CrtAwsSigner> { "Calculated signature: ${crtResult.signature.decodeToString()}" }

        val crtSignedResult = checkNotNull(crtResult.signedRequest) { "Signed request unexpectedly null" }

        val requestBuilder = request.toBuilder()
        requestBuilder.update(crtSignedResult)
        return AwsSigningResult(requestBuilder.build(), crtResult.signature)
    }

    override suspend fun signChunk(
        chunkBody: ByteArray,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit> {
        val crtConfig = config.toCrtSigningConfig()
        val crtResult = CrtSigner.signChunk(chunkBody, prevSignature, crtConfig)
        coroutineContext.debug<CrtAwsSigner> { "Calculated signature: ${crtResult.signature.decodeToString()}" }

        return AwsSigningResult(Unit, crtResult.signature)
    }

    override suspend fun signChunkTrailer(
        trailingHeaders: Headers,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit> {
        val crtConfig = config.toCrtSigningConfig()
        val crtTrailingHeaders = trailingHeaders.toCrtHeaders()

        val crtResult = CrtSigner.signChunkTrailer(crtTrailingHeaders, prevSignature, crtConfig)
        return AwsSigningResult(Unit, crtResult.signature)
    }
}

private fun AwsSignatureType.toCrtSignatureType() = when (this) {
    AwsSignatureType.HTTP_REQUEST_CHUNK -> CrtSignatureType.HTTP_REQUEST_CHUNK
    AwsSignatureType.HTTP_REQUEST_EVENT -> CrtSignatureType.HTTP_REQUEST_EVENT
    AwsSignatureType.HTTP_REQUEST_VIA_HEADERS -> CrtSignatureType.HTTP_REQUEST_VIA_HEADERS
    AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS -> CrtSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
    AwsSignatureType.HTTP_REQUEST_TRAILING_HEADERS -> CrtSignatureType.HTTP_REQUEST_TRAILING_HEADERS
}

private fun AwsSignedBodyHeader.toCrtSignedBodyHeaderType() = when (this) {
    AwsSignedBodyHeader.NONE -> CrtSignedBodyHeaderType.NONE
    AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256 -> CrtSignedBodyHeaderType.X_AMZ_CONTENT_SHA256
}

private fun AwsSigningAlgorithm.toCrtSigningAlgorithm() = when (this) {
    AwsSigningAlgorithm.SIGV4 -> CrtSigningAlgorithm.SIGV4
    AwsSigningAlgorithm.SIGV4_ASYMMETRIC -> CrtSigningAlgorithm.SIGV4_ASYMMETRIC
}

private suspend fun AwsSigningConfig.toCrtSigningConfig(): CrtSigningConfig {
    val src = this
    return CrtSigningConfig.build {
        region = src.region
        service = src.service
        date = src.signingDate.epochMilliseconds
        algorithm = src.algorithm.toCrtSigningAlgorithm()
        shouldSignHeader = src.shouldSignHeader
        signatureType = src.signatureType.toCrtSignatureType()
        useDoubleUriEncode = src.useDoubleUriEncode
        normalizeUriPath = src.normalizeUriPath
        omitSessionToken = src.omitSessionToken
        signedBodyValue = src.hashSpecification.toCrtSignedBodyValue()
        signedBodyHeader = src.signedBodyHeader.toCrtSignedBodyHeaderType()
        credentials = src.credentials.toCrtCredentials()
        expirationInSeconds = src.expiresAfter?.inWholeSeconds ?: 0
    }
}

private fun Credentials.toCrtCredentials() = CrtCredentials(accessKeyId, secretAccessKey, sessionToken)

private fun HashSpecification.toCrtSignedBodyValue(): String? = when (this) {
    is HashSpecification.CalculateFromPayload -> null
    is HashSpecification.HashLiteral -> hash
}

private fun Headers.toCrtHeaders(): CrtHeaders {
    val headersBuilder = aws.sdk.kotlin.crt.http.HeadersBuilder()
    forEach(headersBuilder::appendAll)
    return headersBuilder.build()
}
