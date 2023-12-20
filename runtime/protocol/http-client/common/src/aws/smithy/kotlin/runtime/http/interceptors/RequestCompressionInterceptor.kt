/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.compression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.compression.compressRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import kotlin.coroutines.coroutineContext

private val VALID_COMPRESSION_THRESHOLD_BYTES_RANGE = 0..10_485_760

/**
 * HTTP interceptor that compresses request payloads
 * @param compressionThresholdBytes The threshold for applying compression to a request
 * @param availableCompressionAlgorithms The compression algorithms that are supported by the client
 * @param supportedCompressionAlgorithms The ID's of compression algorithms that are supported by the server
 */
@InternalApi
public class RequestCompressionInterceptor(
    private val compressionThresholdBytes: Long,
    private val availableCompressionAlgorithms: List<CompressionAlgorithm>,
    private val supportedCompressionAlgorithms: List<String>,
) : HttpInterceptor {

    init {
        require(compressionThresholdBytes in VALID_COMPRESSION_THRESHOLD_BYTES_RANGE) { "compressionThresholdBytes ($compressionThresholdBytes) must be in the range $VALID_COMPRESSION_THRESHOLD_BYTES_RANGE" }
    }

    override suspend fun modifyBeforeRetryLoop(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): HttpRequest {
        val payloadSizeBytes = context.protocolRequest.body.contentLength
        val algorithm = supportedCompressionAlgorithms.firstNotNullOfOrNull { id ->
            availableCompressionAlgorithms.find { it.id == id }
        }

        return if (algorithm != null && (context.protocolRequest.body.isStreaming || payloadSizeBytes?.let { it >= compressionThresholdBytes } == true)) {
            algorithm.compressRequest(context.protocolRequest)
        } else {
            val logger = coroutineContext.logger<RequestCompressionInterceptor>()
            val skipCause = if (algorithm == null) "no modeled compression algorithms are supported by the client" else "request size threshold ($compressionThresholdBytes) was not met"

            logger.debug { "skipping request compression because $skipCause" }

            context.protocolRequest
        }
    }

    /**
     * Determines if a http body is streaming type or not.
     */
    private val HttpBody.isStreaming: Boolean
        get() = this is HttpBody.ChannelContent || this is HttpBody.SourceContent || this.contentLength == null
}
