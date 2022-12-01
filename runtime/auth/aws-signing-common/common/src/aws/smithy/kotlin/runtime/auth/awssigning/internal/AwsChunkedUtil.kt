/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.internal

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.auth.awssigning.HashSpecification
import aws.smithy.kotlin.runtime.auth.awssigning.middleware.AwsSigningMiddleware
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.io.SdkBuffer

/**
 * Chunk size used by Transfer-Encoding `aws-chunked`
 */
public const val CHUNK_SIZE_BYTES: Int = 65_536

internal fun SdkBuffer.writeTrailers(
    trailers: Headers,
    signature: String,
) {
    trailers
        .entries()
        .sortedBy { entry -> entry.key.lowercase() }
        .forEach { entry ->
            writeUtf8(entry.key)
            writeUtf8(":")
            writeUtf8(entry.value.joinToString(",") { v -> v.trim() })
            writeUtf8("\r\n")
        }
    writeUtf8("x-amz-trailer-signature:${signature}\r\n")
}

/**
 * @return a boolean representing if this HttpBody is eligible to send via aws-chunked content encoding
 */
internal val HttpBody.isEligibleForAwsChunkedStreaming: Boolean
    get() = (this is HttpBody.SourceContent || this is HttpBody.ChannelContent) && contentLength != null &&
        (isOneShot || contentLength!! > AwsSigningMiddleware.AWS_CHUNKED_THRESHOLD)

/**
 * @return a boolean representing if the signing configuration is configured (via [HashSpecification]) for aws-chunked content encoding
 */
internal val AwsSigningConfig.useAwsChunkedEncoding: Boolean
    get() = when (hashSpecification) {
        is HashSpecification.StreamingAws4HmacSha256Payload, is HashSpecification.StreamingAws4HmacSha256PayloadWithTrailers -> true
        else -> false
    }

/**
 * Set the HTTP headers required for the aws-chunked content encoding
 */
internal fun HttpRequestBuilder.setAwsChunkedHeaders() {
    headers.setMissing("Content-Encoding", "aws-chunked")
    headers.setMissing("Transfer-Encoding", "chunked")
    headers.setMissing("X-Amz-Decoded-Content-Length", body.contentLength!!.toString())
}

/**
 * Update the HTTP body to use aws-chunked content encoding
 */
internal expect fun HttpRequestBuilder.setAwsChunkedBody(signer: AwsSigner, signingConfig: AwsSigningConfig, signature: ByteArray)
