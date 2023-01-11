/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.util.*
import kotlinx.coroutines.*
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
 * @param checksumAlgorithmName the name of the algorithm used to calculate the checksum
 */
@InternalApi
public class FlexibleChecksumsRequestMiddleware(private val checksumAlgorithmName: String) : ModifyRequestMiddleware {
    public override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        val logger = coroutineContext.getLogger<FlexibleChecksumsRequestMiddleware>()

        val headerName = "x-amz-checksum-${checksumAlgorithmName.lowercase()}"
        logger.debug { "Resolved header name: $headerName" }

        // remove all checksum headers except for $headerName
        // this handles the case where a user inputs a precalculated checksum, but it doesn't match the input checksum algorithm
        req.subject.headers.removeAllChecksumHeadersExcept(headerName)

        val checksumAlgorithm = checksumAlgorithmName.toHashFunction() ?: throw ClientException("Could not parse checksum algorithm $checksumAlgorithmName")

        if (!checksumAlgorithm.isSupported) {
            throw ClientException("Checksum algorithm $checksumAlgorithmName is not supported for flexible checksums")
        }

        if (req.subject.body.isEligibleForAwsChunkedStreaming) {
            req.subject.header("x-amz-trailer", headerName)

            val deferredChecksum = CompletableDeferred<String>()

            if (req.subject.headers[headerName] != null) {
                logger.debug { "User supplied a checksum, skipping asynchronous calculation" }

                val checksum = req.subject.headers[headerName]!!
                req.subject.headers.remove(headerName) // remove the checksum header because it will be sent as a trailing header

                deferredChecksum.complete(checksum)
            } else {
                logger.debug { "Calculating checksum asynchronously" }

                req.subject.body = req.subject.body
                    .toHashingBody(checksumAlgorithm, req.subject.body.contentLength)
                    .toCompletingBody(deferredChecksum, req.subject.body.contentLength)
            }

            req.subject.trailingHeaders.append(headerName, deferredChecksum)
        } else if (req.subject.headers[headerName] == null) {
            logger.debug { "Calculating checksum" }

            val checksum = req.subject.body.readAll()?.hash(checksumAlgorithm)?.encodeBase64String() ?:
                throw RuntimeException("Failed to calculate checksum")

            req.subject.header(headerName, checksum)
        }

        return req
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
     * Gets the Base64 encoded checksum of an HttpBody
     * To use this, the HttpBody's underlying data source *must* be either a [HashingSource] or [HashingByteReadChannel],
     * which means the HttpBody must also be either an [HttpBody.SourceContent] or [HttpBody.ChannelContent]. An exception
     * will be thrown otherwise.
     * @return the Base64 encoded checksum of an HttpBody
     */
    public val HttpBody.checksum: String get() = when (this) {
        is HttpBody.SourceContent -> { (readFrom() as HashingSource).digest().encodeBase64String() }
        is HttpBody.ChannelContent -> { (readFrom() as HashingByteReadChannel).digest().encodeBase64String() }
        else -> throw ClientException("HttpBody type is not supported")
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
}
