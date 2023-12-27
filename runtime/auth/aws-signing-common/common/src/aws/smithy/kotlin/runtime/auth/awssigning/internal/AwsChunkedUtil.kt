/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.internal

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.awssigning.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.io.SdkBuffer

/**
 * Chunk size used by Transfer-Encoding `aws-chunked`
 */
public const val CHUNK_SIZE_BYTES: Int = 65_536

/**
 * The minimum size of a streaming body before the SDK will begin using aws-chunked content encoding.
 */
public const val AWS_CHUNKED_THRESHOLD: Int = CHUNK_SIZE_BYTES * 16

internal fun SdkBuffer.writeTrailers(trailers: Headers) {
    trailers
        .entries()
        .sortedBy { entry -> entry.key.lowercase() }
        .forEach { entry ->
            writeUtf8(entry.key)
            writeUtf8(":")
            writeUtf8(entry.value.joinToString(",") { v -> v.trim() })
            writeUtf8("\r\n")
        }
}

internal fun SdkBuffer.writeTrailerSignature(signature: String) {
    writeUtf8("x-amz-trailer-signature:${signature}\r\n")
}

/**
 * @return a boolean representing if this HttpBody is eligible to send via aws-chunked content encoding
 */
@InternalApi
public val HttpBody.isEligibleForAwsChunkedStreaming: Boolean
    get() = (this is HttpBody.SourceContent || this is HttpBody.ChannelContent) &&
        (isOneShot || (contentLength?.compareTo(AWS_CHUNKED_THRESHOLD) ?: 0) > 0)

/**
 * @return a boolean representing if the signing configuration is configured (via [HashSpecification]) for aws-chunked content encoding
 */
@InternalApi
public val AwsSigningConfig.useAwsChunkedEncoding: Boolean
    get() = when (hashSpecification) {
        is HashSpecification.StreamingAws4HmacSha256Payload,
        is HashSpecification.StreamingAws4HmacSha256PayloadWithTrailers,
        is HashSpecification.StreamingUnsignedPayloadWithTrailers,
        -> true
        else -> false
    }

/**
 * Set the HTTP headers required for the aws-chunked content encoding
 */
@InternalApi
public fun HttpRequestBuilder.setAwsChunkedHeaders() {
    headers.append("Content-Encoding", "aws-chunked")
    headers["Transfer-Encoding"] = "chunked"
    headers["X-Amz-Decoded-Content-Length"] = body.contentLength!!.toString()
}

/**
 * Update the HTTP body to use aws-chunked content encoding
 */
@InternalApi
public fun HttpRequestBuilder.setAwsChunkedBody(signer: AwsSigner, signingConfig: AwsSigningConfig, signature: ByteArray, trailingHeaders: DeferredHeaders) {
    body = when (body) {
        is HttpBody.ChannelContent -> AwsChunkedByteReadChannel(
            checkNotNull(body.toSdkByteReadChannel()),
            signer,
            signingConfig,
            signature,
            trailingHeaders,
        ).toHttpBody(-1)

        is HttpBody.SourceContent -> AwsChunkedSource(
            (body as HttpBody.SourceContent).readFrom(),
            signer,
            signingConfig,
            signature,
            trailingHeaders,
        ).toHttpBody(-1)

        else -> throw ClientException("HttpBody type is not supported")
    }
}
