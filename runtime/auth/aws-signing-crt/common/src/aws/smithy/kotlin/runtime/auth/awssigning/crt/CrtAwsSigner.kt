/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning.crt

import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.*
import aws.smithy.kotlin.runtime.crt.ReadChannelBodyStream
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.util.fullUriToQueryParameters
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import kotlin.coroutines.coroutineContext
import aws.sdk.kotlin.crt.auth.credentials.Credentials as CrtCredentials
import aws.sdk.kotlin.crt.auth.signing.AwsSignatureType as CrtSignatureType
import aws.sdk.kotlin.crt.auth.signing.AwsSignedBodyHeaderType as CrtSignedBodyHeaderType
import aws.sdk.kotlin.crt.auth.signing.AwsSigner as CrtSigner
import aws.sdk.kotlin.crt.auth.signing.AwsSigningAlgorithm as CrtSigningAlgorithm
import aws.sdk.kotlin.crt.auth.signing.AwsSigningConfig as CrtSigningConfig
import aws.sdk.kotlin.crt.http.Headers as CrtHeaders
import aws.sdk.kotlin.crt.http.HttpRequest as CrtHttpRequest

object CrtAwsSigner : AwsSigner {
    override suspend fun sign(request: HttpRequest, config: AwsSigningConfig): AwsSigningResult<HttpRequest> {
        val crtRequest = request.toSignableCrtRequest()
        val crtConfig = config.toCrtSigningConfig()

        val crtResult = CrtSigner.sign(crtRequest, crtConfig)
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
        return AwsSigningResult(Unit, crtResult.signature)
    }
}

private fun AwsSignatureType.toCrtSignatureType() = when (this) {
    AwsSignatureType.HTTP_REQUEST_CHUNK -> CrtSignatureType.HTTP_REQUEST_CHUNK
    AwsSignatureType.HTTP_REQUEST_EVENT -> CrtSignatureType.HTTP_REQUEST_EVENT
    AwsSignatureType.HTTP_REQUEST_VIA_HEADERS -> CrtSignatureType.HTTP_REQUEST_VIA_HEADERS
    AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS -> CrtSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
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
    val srcCredentials = src.credentialsProvider.getCredentials()
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
        signedBodyValue = src.bodyHash.hash
        signedBodyHeader = src.signedBodyHeader.toCrtSignedBodyHeaderType()
        credentials = srcCredentials.toCrtCredentials()
        expirationInSeconds = src.expiresAfter?.inWholeSeconds ?: 0
    }
}

private fun Credentials.toCrtCredentials() = CrtCredentials(accessKeyId, secretAccessKey, sessionToken)

private fun Headers.toCrtHeaders(): CrtHeaders {
    val headersBuilder = aws.sdk.kotlin.crt.http.HeadersBuilder()
    forEach(headersBuilder::appendAll)
    return headersBuilder.build()
}

/**
 * Convert an [HttpRequest] into a CRT HttpRequest for the purposes of signing
 */
private suspend fun HttpRequest.toSignableCrtRequest() = CrtHttpRequest(
    method = method.name,
    encodedPath = url.encodedPath,
    headers = headers.toCrtHeaders(),
    body = signableBodyStream(body),
)

private fun HttpRequestBuilder.update(crtRequest: CrtHttpRequest) {
    crtRequest.headers.entries().forEach { entry ->
        headers.appendMissing(entry.key, entry.value)
    }

    if (crtRequest.encodedPath.isNotBlank()) {
        crtRequest.encodedPath.fullUriToQueryParameters()?.let {
            it.forEach { key, values ->
                // The CRT request has a URL-encoded path which means simply appending missing could result in both the
                // raw and percent-encoded value being present. Instead, just append new keys added by signing.
                if (!url.parameters.contains(key)) {
                    url.parameters.appendAll(key, values)
                }
            }
        }
    }
}

private suspend fun signableBodyStream(body: HttpBody): HttpRequestBodyStream? = when {
    body is HttpBody.Bytes -> HttpRequestBodyStream.fromByteArray(body.bytes())
    body is HttpBody.Streaming && body.isReplayable ->
        // FIXME: this is not particularly efficient since we have to launch a coroutine to fill it.
        // see https://github.com/awslabs/smithy-kotlin/issues/436
        ReadChannelBodyStream(body.readFrom(), coroutineContext)
    body is HttpBody.Streaming && !body.isReplayable ->
        // can only consume the stream once
        null
    else -> null
}
