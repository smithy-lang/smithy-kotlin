/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.isStreaming
import aws.smithy.kotlin.runtime.http.request.HttpRequest

private val VALID_COMPRESSION_THRESHOLD_BYTES_RANGE = 0..10485760

/**
 * HTTP interceptor that compresses request payloads
 */
@InternalApi
public class RequestCompressionInterceptor(
    private val compressionThresholdBytes: Long,
    private val supportedCompressionAlgorithms: List<String>,
    private val availableCompressionAlgorithms: List<CompressionAlgorithm>,
) : HttpInterceptor {

    init {
        require(compressionThresholdBytes in VALID_COMPRESSION_THRESHOLD_BYTES_RANGE) { "compressionThresholdBytes ($compressionThresholdBytes) must be in the range $VALID_COMPRESSION_THRESHOLD_BYTES_RANGE" }
    }

    override suspend fun modifyBeforeRetryLoop(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): HttpRequest {
        val payloadSizeBytes = context.protocolRequest.body.contentLength
        val algorithm = availableCompressionAlgorithms.find { available ->
            supportedCompressionAlgorithms.find { available.id == it } != null
        }

        return if (algorithm != null && (context.protocolRequest.body.isStreaming || payloadSizeBytes?.let { it >= compressionThresholdBytes } == true)) {
            algorithm.compress(context.protocolRequest)
        } else {
            context.protocolRequest
        }
    }
}
