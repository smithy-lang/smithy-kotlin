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
import aws.smithy.kotlin.runtime.client.config.HttpChecksumConfigOption
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext

/**
 * Calculates a request's checksum.
 *
 * If a user supplies a checksum via an HTTP header no calculation will be done. The exception is MD5, if a user
 * supplies an MD5 checksum header it will be ignored.
 *
 * If the request configuration and model requires checksum calculation:
 * - Check if the user configured a checksum algorithm for the request and attempt to use that.
 * - If no checksum is configured for the request then use the default checksum algorithm to calculate a checksum.
 *
 * If the request will be streamed:
 * - The checksum calculation is done asynchronously using a hashing & completing body.
 * - The checksum will be sent in a trailing header, once the request is consumed.
 *
 * If the request will not be streamed:
 * - The checksum calculation is done synchronously
 * - The checksum will be sent in a header
 *
 * Business metrics MUST be emitted for the checksum algorithm used.
 *
 * @param requestChecksumRequired Model sourced flag indicating if checksum calculation is mandatory.
 * @param requestChecksumCalculation Configuration option that determines when checksum calculation should be done.
 * @param userSelectedChecksumAlgorithm The checksum algorithm that the user selected for the request, may be null.
 */
@InternalApi
public class FlexibleChecksumsRequestInterceptor(
    requestChecksumRequired: Boolean,
    requestChecksumCalculation: HttpChecksumConfigOption?,
    userSelectedChecksumAlgorithm: String?,
) : AbstractChecksumInterceptor() {
    private val forcedToCalculateChecksum = requestChecksumRequired || requestChecksumCalculation == HttpChecksumConfigOption.WHEN_SUPPORTED
    private val checksumHeader = StringBuilder("x-amz-checksum-")
    private val defaultChecksumAlgorithm = lazy { Crc32() }
    private val defaultChecksumAlgorithmHeaderPostfix = "crc32"

    private val checksumAlgorithm = userSelectedChecksumAlgorithm?.let {
        val hashFunction = userSelectedChecksumAlgorithm.toHashFunction()
        if (hashFunction == null || !hashFunction.isSupported) {
            throw ClientException("Checksum algorithm '$userSelectedChecksumAlgorithm' is not supported for flexible checksums")
        }
        checksumHeader.append(userSelectedChecksumAlgorithm.lowercase())
        hashFunction
    } ?: if (forcedToCalculateChecksum) {
        checksumHeader.append(defaultChecksumAlgorithmHeaderPostfix)
        defaultChecksumAlgorithm.value
    } else {
        null
    }

    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        val logger = coroutineContext.logger<FlexibleChecksumsRequestInterceptor>()

        userProviderChecksumHeader(context.protocolRequest, logger)?.let {
            logger.debug { "User supplied a checksum via header, skipping checksum calculation" }

            val request = context.protocolRequest.toBuilder()
            request.headers.removeAllChecksumHeadersExcept(it)
            return request.build()
        }

        if (checksumAlgorithm == null) {
            logger.debug { "User didn't select a checksum algorithm and checksum calculation isn't required, skipping checksum calculation" }
            return context.protocolRequest
        }

        logger.debug { "Calculating checksum using '$checksumAlgorithm'" }

        val request = context.protocolRequest.toBuilder()

        if (request.body.isEligibleForAwsChunkedStreaming) {
            val deferredChecksum = CompletableDeferred<String>(context.executionContext.coroutineContext.job)

            request.body = request.body
                .toHashingBody(
                    checksumAlgorithm,
                    request.body.contentLength,
                )
                .toCompletingBody(
                    deferredChecksum,
                )

            request.headers.append("x-amz-trailer", checksumHeader.toString())
            request.trailingHeaders.append(checksumHeader.toString(), deferredChecksum)
        } else {
            checksumAlgorithm.update(
                request.body.readAll() ?: byteArrayOf(),
            )
            request.headers[checksumHeader.toString()] = checksumAlgorithm.digest().encodeBase64String()
        }

        context.executionContext.emitBusinessMetric(checksumAlgorithm.toBusinessMetric())
        request.headers.removeAllChecksumHeadersExcept(checksumHeader.toString())

        return request.build()
    }

    override suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String? {
        val req = context.protocolRequest.toBuilder()

        if (checksumAlgorithm == null) return null

        return when {
            req.body.contentLength == null && !req.body.isOneShot -> {
                val channel = req.body.toSdkByteReadChannel()!!
                channel.rollingHash(checksumAlgorithm).encodeBase64String()
            }
            else -> {
                val bodyBytes = req.body.readAll()!!
                req.body = bodyBytes.toHttpBody()
                bodyBytes.hash(checksumAlgorithm).encodeBase64String()
            }
        }
    }

    override fun applyChecksum(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
        checksum: String,
    ): HttpRequest {
        val req = context.protocolRequest.toBuilder()

        if (!req.headers.contains(checksumHeader.toString())) {
            req.header(checksumHeader.toString(), checksum)
        }

        return req.build()
    }

    // FIXME this duplicates the logic from aws-signing-common, but can't import from there due to circular import.
    private val HttpBody.isEligibleForAwsChunkedStreaming: Boolean
        get() = (this is HttpBody.SourceContent || this is HttpBody.ChannelContent) &&
            contentLength != null &&
            (isOneShot || contentLength!! > 65536 * 16)

    /**
     * @return if the [HashFunction] is supported by flexible checksums
     */
    private val HashFunction.isSupported: Boolean get() = when (this) {
        is Crc32, is Crc32c, is Sha256, is Sha1 -> true
        else -> false
    }

    /**
     * Removes all checksum headers except [headerName]
     * @param headerName the checksum header name to keep
     */
    private fun HeadersBuilder.removeAllChecksumHeadersExcept(headerName: String) {
        names().forEach { name ->
            if (name.startsWith("x-amz-checksum-") && name != headerName) {
                remove(name)
            }
        }
    }

    /**
     * Convert an [HttpBody] with an underlying [HashingSource] or [HashingByteReadChannel]
     * to a [CompletingSource] or [CompletingByteReadChannel], respectively.
     */
    private fun HttpBody.toCompletingBody(deferred: CompletableDeferred<String>) = when (this) {
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
     * MD5 is not considered a valid checksum algorithm.
     */
    private fun userProviderChecksumHeader(request: HttpRequest, logger: Logger): String? {
        request.headers.entries().forEach { header ->
            val headerName = header.key.lowercase()
            if (headerName.startsWith("x-amz-checksum-")) {
                if (headerName.endsWith("md5")) {
                    logger.debug {
                        "User provided md5 request checksum via headers, md5 is not a valid algorithm, ignoring header"
                    }
                } else {
                    return headerName
                }
            }
        }
        return null
    }

    /**
     * Maps supported hash functions to business metrics.
     */
    private fun HashFunction.toBusinessMetric(): BusinessMetric = when (this) {
        is Crc32 -> SmithyBusinessMetric.FLEXIBLE_CHECKSUMS_REQ_CRC32
        is Crc32c -> SmithyBusinessMetric.FLEXIBLE_CHECKSUMS_REQ_CRC32C
        is Sha1 -> SmithyBusinessMetric.FLEXIBLE_CHECKSUMS_REQ_SHA1
        is Sha256 -> SmithyBusinessMetric.FLEXIBLE_CHECKSUMS_REQ_SHA256
        else -> throw IllegalStateException("Checksum was calculated using an unsupported hash function: $this")
    }
}
