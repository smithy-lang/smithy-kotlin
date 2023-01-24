/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder
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
 * @param checksumAlgorithmNameInitializer a function which parses the input [I] to return the checksum algorithm name
 */
@InternalApi
public class FlexibleChecksumsRequestInterceptor<I>(
    private val checksumAlgorithmNameInitializer: (I) -> String?
) : HttpInterceptor {
    private var checksumAlgorithmName = CompletableDeferred<String>()

    override fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        val input = context.request as I
        checksumAlgorithmNameInitializer(input)?.let { checksumAlgorithmName.complete(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun modifyBeforeRetryLoop(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        val logger = coroutineContext.getLogger<FlexibleChecksumsRequestInterceptor<I>>()
        if (!checksumAlgorithmName.isCompleted) {
            logger.debug { "no checksum algorithm specified, skipping flexible checksums processing" }
            return context.protocolRequest
        }

        val req = context.protocolRequest.toBuilder()

        check(context.protocolRequest.body.contentLength != null && context.protocolRequest.body.contentLength!! > 0) {
            "Can't calculate the checksum of an empty body"
        }

        val headerName = "x-amz-checksum-${checksumAlgorithmName.getCompleted()}"
        logger.debug { "Resolved checksum header name: $headerName" }

        // remove all checksum headers except for $headerName
        // this handles the case where a user inputs a precalculated checksum, but it doesn't match the input checksum algorithm
        req.headers.removeAllChecksumHeadersExcept(headerName)

        val checksumAlgorithm = checksumAlgorithmName.getCompleted().toHashFunction() ?: throw ClientException("Could not parse checksum algorithm $checksumAlgorithmName")

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
                req.body = req.body.toHashingBody(checksumAlgorithm, req.body.contentLength, deferredChecksum)
            }

            req.trailingHeaders.append(headerName, deferredChecksum)
        } else if (req.headers[headerName] == null) {
            logger.debug { "Calculating checksum" }

            val bodyBytes = req.body.readAll()!!
            req.body = bodyBytes.toHttpBody() // replace the consumed body

            val checksum = bodyBytes.hash(checksumAlgorithm).encodeBase64String()
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
}
