/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.SmithyBusinessMetric
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
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
) : AbstractChecksumInterceptor() {
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
            if (context.protocolRequest.body.isEligibleForAwsChunkedStreaming) { // Handle checksum calculation here
                logger.debug { "Calculating checksum during transmission using: ${checksumAlgorithm::class.simpleName}" }

                val request = context.protocolRequest.toBuilder()
                val deferredChecksum = CompletableDeferred<String>(context.executionContext.coroutineContext.job)
                val checksumHeader = checksumAlgorithm.resolveChecksumAlgorithmHeaderName()

                request.body = request.body
                    .toHashingBody(checksumAlgorithm, request.body.contentLength)
                    .toCompletingBody(deferredChecksum)

                request.headers.append("x-amz-trailer", checksumHeader)
                request.trailingHeaders.append(checksumHeader, deferredChecksum)
                context.executionContext.emitBusinessMetric(checksumAlgorithm.toBusinessMetric())
            } else { // Delegate checksum calculation to super class, calculateChecksum, and applyChecksum
                return super.modifyBeforeSigning(context)
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

    // Handles calculating checksum for non-aws-chunked requests
    override suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String? {
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

    // Handles applying checksum for non-aws-chunked requests
    override fun applyChecksum(
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

    // FIXME this duplicates the logic from aws-signing-common, but can't import from there due to circular import.
    private val HttpBody.isEligibleForAwsChunkedStreaming: Boolean
        get() = (this is HttpBody.SourceContent || this is HttpBody.ChannelContent) &&
            contentLength != null &&
            (isOneShot || contentLength!! > 65536 * 16)

    /**
     * Compute the rolling hash of an [SdkByteReadChannel] using [hashFunction], reading up-to [bufferSize] bytes into memory
     * @return a ByteArray of the hash function's digest
     */
    private suspend fun SdkByteReadChannel.rollingHash(hashFunction: HashFunction, bufferSize: Long = 8192): ByteArray {
        val buffer = SdkBuffer()
        while (!isClosedForRead) {
            read(buffer, bufferSize)
            hashFunction.update(buffer.readToByteArray())
        }
        return hashFunction.digest()
    }

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
     * Maps supported hash functions to business metrics.
     */
    private fun HashFunction.toBusinessMetric(): BusinessMetric = when (this) {
        is Crc32 -> SmithyBusinessMetric.FLEXIBLE_CHECKSUMS_REQ_CRC32
        is Crc32c -> SmithyBusinessMetric.FLEXIBLE_CHECKSUMS_REQ_CRC32C
        is Sha1 -> SmithyBusinessMetric.FLEXIBLE_CHECKSUMS_REQ_SHA1
        is Sha256 -> SmithyBusinessMetric.FLEXIBLE_CHECKSUMS_REQ_SHA256
        else -> throw IllegalStateException("Checksum was calculated using an unsupported hash function: ${this::class.simpleName}")
    }

    /**
     * Removes all checksum headers except [headerName]
     * @param headerName the checksum header name to keep
     */
    private fun HeadersBuilder.removeAllChecksumHeadersExcept(headerName: String) =
        names()
            .filter { it.startsWith("x-amz-checksum-", ignoreCase = true) && !it.equals(headerName, ignoreCase = true) }
            .forEach { remove(it) }

    /**
     * Convert an [HttpBody] with an underlying [HashingSource] or [HashingByteReadChannel]
     * to a [CompletingSource] or [CompletingByteReadChannel], respectively.
     */
    private fun HttpBody.toCompletingBody(deferred: CompletableDeferred<String>): HttpBody = when (this) {
        is HttpBody.SourceContent -> CompletingSource(deferred, (readFrom() as HashingSource)).toHttpBody(contentLength)
        is HttpBody.ChannelContent -> CompletingByteReadChannel(deferred, (readFrom() as HashingByteReadChannel)).toHttpBody(contentLength)
        else -> throw ClientException("HttpBody type is not supported")
    }

    /**
     * An [SdkSource] which uses the underlying [hashingSource]'s checksum to complete a [CompletableDeferred] value.
     */
    internal class CompletingSource(
        private val deferred: CompletableDeferred<String>,
        private val hashingSource: HashingSource,
    ) : SdkSource by hashingSource {
        override fun read(sink: SdkBuffer, limit: Long): Long = hashingSource.read(sink, limit)
            .also {
                if (it == -1L) {
                    deferred.complete(hashingSource.digest().encodeBase64String())
                }
            }
    }

    /**
     * An [SdkByteReadChannel] which uses the underlying [hashingChannel]'s checksum to complete a [CompletableDeferred] value.
     */
    internal class CompletingByteReadChannel(
        private val deferred: CompletableDeferred<String>,
        private val hashingChannel: HashingByteReadChannel,
    ) : SdkByteReadChannel by hashingChannel {
        override suspend fun read(sink: SdkBuffer, limit: Long): Long = hashingChannel.read(sink, limit)
            .also {
                if (it == -1L) {
                    deferred.complete(hashingChannel.digest().encodeBase64String())
                }
            }
    }
}
