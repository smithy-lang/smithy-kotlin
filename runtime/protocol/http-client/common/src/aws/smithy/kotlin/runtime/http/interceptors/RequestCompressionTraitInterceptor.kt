/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder

private val VALID_COMPRESSION_RANGE = 0..10485760

/**
 * HTTP interceptor that compresses operation request payloads when eligible
 */
@InternalApi
public class RequestCompressionTraitInterceptor(
    private val compressionThreshold: Int,
    private val requestedCompressionAlgorithms: List<String>,
    private val supportedCompressionAlgorithms: List<CompressionAlgorithm>,
) : HttpInterceptor {

    init {
        require(compressionThreshold in VALID_COMPRESSION_RANGE) { "compressionThresholdBytes ($compressionThreshold) must be in the range $VALID_COMPRESSION_RANGE" }
    }

    override suspend fun modifyBeforeRetryLoop(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): HttpRequest {
        val payloadSize = context.protocolRequest.body.contentLength
        val streamingPayload = payloadSize == null
        if (streamingPayload || payloadSize!! >= compressionThreshold) {
            supportedCompressionAlgorithms.find { supported ->
                requestedCompressionAlgorithms.find { supported.id == it } != null
            }?.let { algorithm ->

                val request = context.protocolRequest.toBuilder()
                request.body = algorithm.compress(request.body)
                addHeader(request, algorithm.id)
                return request.build()
            }
        }
        return context.protocolRequest
    }
}

/**
 * Appends the algorithm id to the content encoding header. Doesn't remove old content encodings if already present
 * in header
 */
private fun addHeader(request: HttpRequestBuilder, algorithmId: String) {
    val previousEncodings = request.headers["Content-Encoding"]
    val contentEncodingHeaderPrefix = previousEncodings?.let { "$previousEncodings, " } ?: ""

    request.headers.remove("Content-Encoding")
    request.header(
        "Content-Encoding",
        "$contentEncodingHeaderPrefix$algorithmId",
    )
}
