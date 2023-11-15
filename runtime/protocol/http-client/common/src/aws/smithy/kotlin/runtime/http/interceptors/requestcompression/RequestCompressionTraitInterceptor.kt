/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.util.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder

private val VALID_COMPRESSION_RANGE = 0..10485760

/**
 * HTTP interceptor that compresses operation request payloads when eligible
 */
public class RequestCompressionTraitInterceptor(
        private val compressionThreshold: Int,
        private val requestedCompressionAlgorithms: List<String>,
        private val supportedCompressionAlgorithms: List<CompressionAlgorithm>,
) : HttpInterceptor {

    // Verify min compression size setting is in range
    init {
        require(compressionThreshold in VALID_COMPRESSION_RANGE) { "compressionThresholdBytes ($compressionThreshold) must be in the range $VALID_COMPRESSION_RANGE" }
    }

    override suspend fun modifyBeforeRetryLoop(
            context: ProtocolRequestInterceptorContext<Any, HttpRequest>
    ): HttpRequest {

        // Determine if going forward with compression
        val payloadSize = context.protocolRequest.body.contentLength
        val streamingPayload = payloadSize == null
        if (streamingPayload || payloadSize!! >= compressionThreshold) {

            // Check if requested algorithm(s) is supported
            supportedCompressionAlgorithms.find { supported ->
                requestedCompressionAlgorithms.find { supported.id == it } != null
            }?.let { algorithm ->

                // Attempt compression
                val request = context.protocolRequest.toBuilder()
                request.body = request.body
                // TODO: Write compression
                request.headers.append("Content-Encoding", algorithm.id)
                return request.build()
            }
        }
        return context.protocolRequest
    }
}