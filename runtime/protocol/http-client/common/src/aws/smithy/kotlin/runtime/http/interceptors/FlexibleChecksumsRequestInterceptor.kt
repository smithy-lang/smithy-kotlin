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
import aws.smithy.kotlin.runtime.client.config.ChecksumConfigOption
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext

/**
 * TODO -
 */
@InternalApi
public class FlexibleChecksumsRequestInterceptor(
    requestChecksumRequired: Boolean,
    requestChecksumCalculation: ChecksumConfigOption?,
    private val userSelectedChecksumAlgorithm: String?,
) : AbstractChecksumInterceptor() {
    private val forcedToCalculateChecksum = requestChecksumRequired || requestChecksumCalculation == ChecksumConfigOption.WHEN_SUPPORTED
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

        userProviderChecksumHeader(context.protocolRequest)?.let {
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

//        throw Exception("\nBody type: ${request.body::class.simpleName}\nEligible for chunked streaming: ${request.body.isEligibleForAwsChunkedStreaming}\nContent Length: ${request.body.contentLength}\nIs one shot: ${request.body.isOneShot}")

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
    private fun userProviderChecksumHeader(request: HttpRequest): String? {
        request.headers.entries().forEach { header ->
            val headerName = header.key.lowercase()
            if (headerName.startsWith("x-amz-checksum-") && !headerName.endsWith("md5")) {
                return headerName
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
