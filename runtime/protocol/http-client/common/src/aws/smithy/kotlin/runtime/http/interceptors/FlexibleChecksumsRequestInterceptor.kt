/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import aws.smithy.kotlin.runtime.util.LazyAsyncValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext

/**
 * Mutate a request to enable flexible checksums.
 *
 * If the checksum will be sent as a header, calculate the checksum.
 *
 * Otherwise, if it will be sent as a trailing header, calculate the checksum as asynchronously as the body is streamed.
 * In this case, a [LazyAsyncValue] will be added to the execution context which allows the trailing checksum to be sent
 * after the entire body has been streamed.
 *
 * @param checksumAlgorithmNameInitializer an optional function which parses the input [I] to return the checksum algorithm name.
 * if not set, then the [HttpOperationContext.ChecksumAlgorithm] execution context attribute will be used.
 */
@InternalApi
public class FlexibleChecksumsRequestInterceptor<I>(
    private val checksumAlgorithmNameInitializer: ((I) -> String?)? = null,
) : AbstractChecksumInterceptor() {
    private var checksumAlgorithmName: String? = null

    override fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        @Suppress("UNCHECKED_CAST")
        val input = context.request as I
        checksumAlgorithmName = checksumAlgorithmNameInitializer?.invoke(input)
    }

    private val ExecutionContext.checksumAlgorithm: String? get() =
        this@FlexibleChecksumsRequestInterceptor.checksumAlgorithmName
            ?: this.getOrNull(HttpOperationContext.ChecksumAlgorithm)

    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        val logger = coroutineContext.logger<FlexibleChecksumsRequestInterceptor<I>>()

        val checksumAlgorithmName = context.executionContext.checksumAlgorithm

        checksumAlgorithmName ?: run {
            logger.debug { "no checksum algorithm specified, skipping flexible checksums processing" }
            return context.protocolRequest
        }

        val req = context.protocolRequest.toBuilder()

        check(context.protocolRequest.body !is HttpBody.Empty) {
            "Can't calculate the checksum of an empty body"
        }

        val headerName = "x-amz-checksum-$checksumAlgorithmName".lowercase()
        logger.debug { "Resolved checksum header name: $headerName" }

        // remove all checksum headers except for $headerName
        // this handles the case where a user inputs a precalculated checksum, but it doesn't match the input checksum algorithm
        req.headers.removeAllChecksumHeadersExcept(headerName)

        val checksumAlgorithm = checksumAlgorithmName.toHashFunction() ?: throw ClientException("Could not parse checksum algorithm $checksumAlgorithmName")

        if (!checksumAlgorithm.isSupported) {
            throw ClientException("Checksum algorithm $checksumAlgorithmName is not supported for flexible checksums")
        }

        if (req.body.isEligibleForAwsChunkedStreaming) {
            req.header("x-amz-trailer", headerName)

            val deferredChecksum = CompletableDeferred<String>(context.executionContext.coroutineContext.job)

            if (req.headers[headerName] != null) {
                logger.debug { "User supplied a checksum, skipping asynchronous calculation" }

                val checksum = req.headers[headerName]!!
                req.headers.remove(headerName) // remove the checksum header because it will be sent as a trailing header

                deferredChecksum.complete(checksum)
            } else {
                logger.debug { "Calculating checksum asynchronously" }
                req.body = req.body
                    .toHashingBody(checksumAlgorithm, req.body.contentLength)
                    .toCompletingBody(deferredChecksum)
            }

            req.trailingHeaders.append(headerName, deferredChecksum)
            return req.build()
        } else {
            return super.modifyBeforeSigning(context)
        }
    }

    override suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String? {
        val req = context.protocolRequest.toBuilder()
        val checksumAlgorithm = context.executionContext.checksumAlgorithm?.toHashFunction() ?: return null

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
        checksum: String?,
    ): HttpRequest {
        val checksumAlgorithmName = context.executionContext.checksumAlgorithm
        val headerName = "x-amz-checksum-$checksumAlgorithmName".lowercase()

        val req = context.protocolRequest.toBuilder()

        checksum?.let {
            req.header(headerName, checksum)
        }

        return req.build()
    }

    // FIXME this duplicates the logic from aws-signing-common, but can't import from there due to circular import.
    private val HttpBody.isEligibleForAwsChunkedStreaming: Boolean
        get() = (this is HttpBody.SourceContent || this is HttpBody.ChannelContent) && contentLength != null &&
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
}
