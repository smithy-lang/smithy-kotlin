/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder

private val VALID_COMPRESSION_THRESHOLD_BYTES_RANGE = 0..10485760

/**
 * HTTP interceptor that compresses operation request payloads when eligible.
 */
@InternalApi
public class RequestCompressionTraitInterceptor(
    private val compressionThresholdBytes: Int,
    private val supportedCompressionAlgorithms: List<String>,
    private val availableCompressionAlgorithms: List<CompressionAlgorithm>,
) : HttpInterceptor {

    init {
        require(compressionThresholdBytes in VALID_COMPRESSION_THRESHOLD_BYTES_RANGE) { "compressionThresholdBytes ($compressionThresholdBytes) must be in the range $VALID_COMPRESSION_THRESHOLD_BYTES_RANGE" }
    }

    override suspend fun modifyBeforeRetryLoop(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): HttpRequest {
        val payloadSize = context.protocolRequest.body.contentLength
        val algorithm = availableCompressionAlgorithms.find { available ->
            supportedCompressionAlgorithms.find { available.id == it } != null
        }

        return if (algorithm != null && (context.protocolRequest.body.isStreaming || payloadSize!! >= compressionThresholdBytes)) {
            val request = context.protocolRequest.toBuilder()
            request.body = algorithm.compress(request.body)
            request.headers.append("Content-Encoding", algorithm.id)
            request.build()
        } else {
            context.protocolRequest
        }
    }
}

/**
 * Determines if a http body is streaming type or not.
 */
private val HttpBody.isStreaming: Boolean
    get() = when (this) {
        is HttpBody.ChannelContent, is HttpBody.SourceContent -> true
        else -> false
    }
