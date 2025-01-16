/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.businessmetrics.emitBusinessMetric
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.client.config.RequestHttpChecksumConfig
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import kotlin.coroutines.coroutineContext

/**
 * Handles request checksums for operations with the [HttpChecksumTrait] applied.
 *
 * If a user supplies a checksum via an HTTP header no calculation will be done. The exception is MD5, if a user
 * supplies an MD5 checksum header it will be ignored.
 *
 * If the request configuration and model requires checksum calculation:
 * - Check if the user configured a checksum algorithm for the request and attempt to use that.
 * - If no checksum is configured for the request then use the default checksum algorithm to calculate a checksum.
 *
 * If the request will be streamed:
 * - The checksum calculation is done during transmission using a hashing & completing body.
 * - The checksum will be sent in a trailing header, once the request is consumed.
 *
 * If the request will not be streamed:
 * - The checksum calculation is done before transmission
 * - The checksum will be sent in a header
 *
 * Business metrics MUST be emitted for the checksum algorithm used.
 *
 * @param requestChecksumRequired Model sourced flag indicating if checksum calculation is mandatory.
 * @param requestChecksumCalculation Configuration option that determines when checksum calculation should be done.
 * @param requestChecksumAlgorithm The checksum algorithm that the user selected for the request, may be null.
 */
@InternalApi
public class FlexibleChecksumsRequestInterceptor(
    private val requestChecksumRequired: Boolean,
    private val requestChecksumCalculation: RequestHttpChecksumConfig?,
    private val requestChecksumAlgorithm: String?,
) : CachingChecksumInterceptor() {
    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        val logger = coroutineContext.logger<FlexibleChecksumsRequestInterceptor>()

        context.protocolRequest.userProvidedChecksumHeader(logger)?.let {
            logger.debug { "Checksum was supplied via header: skipping checksum calculation" }

            val request = context.protocolRequest.toBuilder()
            request.headers.removeAllChecksumHeadersExcept(it)
            return context.protocolRequest
        }

        resolveChecksumAlgorithm(
            requestChecksumRequired,
            requestChecksumCalculation,
            requestChecksumAlgorithm,
            context,
        )?.let { checksumAlgorithm ->
            return if (context.protocolRequest.body.isEligibleForAwsChunkedStreaming) {
                logger.debug { "Calculating checksum during transmission using: ${checksumAlgorithm::class.simpleName}" }
                calculateAwsChunkedStreamingChecksum(context, checksumAlgorithm)
            } else {
                if (context.protocolRequest.body is HttpBody.Bytes) {
                    // Cache checksum
                    super.modifyBeforeSigning(context)
                } else {
                    val checksum = calculateFlexibleChecksumsChecksum(context)
                    applyFlexibleChecksumsChecksum(context, checksum)
                }
            }
        }

        logger.debug { "Checksum wasn't provided, selected, or isn't required: skipping checksum calculation" }
        return context.protocolRequest
    }

    /**
     * Determines what checksum algorithm to use, null if none is required
     */
    private fun resolveChecksumAlgorithm(
        requestChecksumRequired: Boolean,
        requestChecksumCalculation: RequestHttpChecksumConfig?,
        requestChecksumAlgorithm: String?,
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): HashFunction? =
        requestChecksumAlgorithm
            ?.toHashFunctionOrThrow()
            ?.takeIf { it.isSupportedForFlexibleChecksums }
            ?: context.defaultChecksumAlgorithmName
                ?.toHashFunctionOrThrow()
                ?.takeIf {
                    (requestChecksumRequired || requestChecksumCalculation == RequestHttpChecksumConfig.WHEN_SUPPORTED) &&
                        it.isSupportedForFlexibleChecksums
                }

    /**
     * Calculates a checksum based on the requirements and limitations of [FlexibleChecksumsRequestInterceptor]
     */
    private suspend fun calculateFlexibleChecksumsChecksum(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): String {
        val req = context.protocolRequest.toBuilder()
        val checksumAlgorithm = resolveChecksumAlgorithm(
            requestChecksumRequired,
            requestChecksumCalculation,
            requestChecksumAlgorithm,
            context,
        )!!

        return when {
            req.body.contentLength == null && !req.body.isOneShot -> {
                val channel = req.body.toSdkByteReadChannel()!!
                channel.rollingHash(checksumAlgorithm).encodeBase64String()
            }
            else -> {
                val bodyBytes = req.body.readAll() ?: byteArrayOf()
                if (req.body.isOneShot) req.body = bodyBytes.toHttpBody()
                bodyBytes.hash(checksumAlgorithm).encodeBase64String()
            }
        }
    }

    override suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String? =
        calculateFlexibleChecksumsChecksum(context)

    /**
     * Applies a checksum based on the requirements and limitations of [FlexibleChecksumsRequestInterceptor]
     */
    private fun applyFlexibleChecksumsChecksum(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
        checksum: String,
    ): HttpRequest {
        val request = context.protocolRequest.toBuilder()
        val checksumAlgorithm = resolveChecksumAlgorithm(
            requestChecksumRequired,
            requestChecksumCalculation,
            requestChecksumAlgorithm,
            context,
        )!!
        val checksumHeader = checksumAlgorithm.resolveChecksumAlgorithmHeaderName()

        request.headers[checksumHeader] = checksum
        request.headers.removeAllChecksumHeadersExcept(checksumHeader)
        context.executionContext.emitBusinessMetric(checksumAlgorithm.toBusinessMetric())

        return request.build()
    }

    override fun applyChecksum(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
        checksum: String,
    ): HttpRequest = applyFlexibleChecksumsChecksum(context, checksum)

    /**
     * Checks if a user provided a checksum for a request via an HTTP header.
     * The header must start with "x-amz-checksum-" followed by the checksum algorithm's name.
     * MD5 is not considered a supported checksum algorithm.
     */
    private fun HttpRequest.userProvidedChecksumHeader(logger: Logger) = headers
        .names()
        .firstOrNull {
            it.startsWith("x-amz-checksum-", ignoreCase = true) &&
                !it.equals("x-amz-checksum-md5", ignoreCase = true).also { isMd5 ->
                    if (isMd5) {
                        logger.debug { "MD5 checksum was supplied via header, MD5 is not a supported algorithm, ignoring header" }
                    }
                }
        }

    /**
     * Removes all checksum headers except [headerName]
     * @param headerName the checksum header name to keep
     */
    private fun HeadersBuilder.removeAllChecksumHeadersExcept(headerName: String) =
        names()
            .filter { it.startsWith("x-amz-checksum-", ignoreCase = true) && !it.equals(headerName, ignoreCase = true) }
            .forEach { remove(it) }
}
