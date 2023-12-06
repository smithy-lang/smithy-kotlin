/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.compression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import kotlin.coroutines.coroutineContext

private val VALID_COMPRESSION_THRESHOLD_BYTES_RANGE = 0..10_485_760

/**
 * HTTP interceptor that compresses request payloads
 * @param compressionThresholdBytes The threshold for applying compression to a request
 * @param supportedCompressionAlgorithms The ID's of compression algorithms that are supported by the server
 * @param availableCompressionAlgorithms The compression algorithms that are supported by the client
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
        val algorithm = findMatch(supportedCompressionAlgorithms, availableCompressionAlgorithms)

        return if (algorithm != null && (context.protocolRequest.body.isStreaming || payloadSizeBytes?.let { it >= compressionThresholdBytes } == true)) {
            val compressedRequest = context.protocolRequest.toBuilder()
            val uncompressedBody = context.protocolRequest.body

            compressedRequest.body = when (uncompressedBody) {
                is HttpBody.SourceContent -> algorithm.compressSdkSource(uncompressedBody.readFrom(), uncompressedBody.contentLength).toHttpBody()
                is HttpBody.ChannelContent -> algorithm.compressSdkByteReadChannel(uncompressedBody.readFrom()).toHttpBody()
                is HttpBody.Bytes -> algorithm.compressBytes(uncompressedBody.bytes()).toHttpBody()
                is HttpBody.Empty -> uncompressedBody
                else -> throw IllegalStateException("HttpBody type '$uncompressedBody' is not supported")
            }

            compressedRequest.headers.append("Content-Encoding", algorithm.contentEncoding)

            return compressedRequest.build()
        } else {
            val logger = coroutineContext.logger<RequestCompressionInterceptor>()
            val skipCause = if (algorithm == null) "requested compression algorithm(s) are not supported by the client" else "request size threshold ($compressionThresholdBytes) was not met"

            logger.debug { "skipping request compression because $skipCause" }

            context.protocolRequest
        }
    }

    private fun findMatch(
        supportedCompressionAlgorithms: List<String>,
        availableCompressionAlgorithms: List<CompressionAlgorithm>,
    ): CompressionAlgorithm? {
        supportedCompressionAlgorithms.forEach { supportedId ->
            availableCompressionAlgorithms.forEach { algorithm ->
                if (algorithm.id == supportedId) return algorithm
            }
        }
        return null
    }
}

/**
 * Determines if a http body is streaming type or not.
 */
private val HttpBody.isStreaming: Boolean
    get() = this is HttpBody.ChannelContent || this is HttpBody.SourceContent || this.contentLength == null
