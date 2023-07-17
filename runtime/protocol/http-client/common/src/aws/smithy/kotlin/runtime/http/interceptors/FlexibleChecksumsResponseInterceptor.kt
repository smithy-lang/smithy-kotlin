/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.client.RequestInterceptorContext
import aws.smithy.kotlin.runtime.hashing.toHashFunction
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.operation.getLogger
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.toHashingBody
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlin.coroutines.coroutineContext

// The priority to validate response checksums, if multiple are present
internal val CHECKSUM_HEADER_VALIDATION_PRIORITY_LIST: List<String> = listOf(
    "x-amz-checksum-crc32c",
    "x-amz-checksum-crc32",
    "x-amz-checksum-sha1",
    "x-amz-checksum-sha256",
)

/**
 * Validate a response's checksum.
 *
 * Wraps the response in a hashing body, calculating the checksum as the response is streamed to the user.
 * The checksum is validated after the user has consumed the entire body using a checksum validating body.
 * Users can check which checksum was validated by referencing the `ResponseChecksumValidated` execution context variable.
 *
 * @param shouldValidateResponseChecksumInitializer A function which uses the input [I] to return whether response checksum validation should occur
 */

@InternalApi
public class FlexibleChecksumsResponseInterceptor<I>(
    private val shouldValidateResponseChecksumInitializer: (input: I) -> Boolean,
) : HttpInterceptor {

    private var shouldValidateResponseChecksum: Boolean = false

    @InternalApi
    public companion object {
        // The name of the checksum header which was validated. If `null`, validation was not performed.
        public val ChecksumHeaderValidated: AttributeKey<String> = AttributeKey("ChecksumHeaderValidated")
    }

    override fun readBeforeSerialization(context: RequestInterceptorContext<Any>) {
        @Suppress("UNCHECKED_CAST")
        val input = context.request as I
        shouldValidateResponseChecksum = shouldValidateResponseChecksumInitializer(input)
    }

    override suspend fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
        if (!shouldValidateResponseChecksum) { return context.protocolResponse }

        val logger = coroutineContext.getLogger<FlexibleChecksumsResponseInterceptor<I>>()

        val checksumHeader = CHECKSUM_HEADER_VALIDATION_PRIORITY_LIST
            .firstOrNull { context.protocolResponse.headers.contains(it) } ?: run {
            logger.warn { "User requested checksum validation, but the response headers did not contain any valid checksums" }
            return context.protocolResponse
        }

        // let the user know which checksum will be validated
        logger.debug { "Validating checksum from $checksumHeader" }
        context.executionContext[ChecksumHeaderValidated] = checksumHeader

        val checksumAlgorithm = checksumHeader.removePrefix("x-amz-checksum-").toHashFunction() ?: throw ClientException("could not parse checksum algorithm from header $checksumHeader")

        // Wrap the response body in a hashing body
        return context.protocolResponse.copy(
            body = context.protocolResponse.body
                .toHashingBody(checksumAlgorithm, context.protocolResponse.body.contentLength)
                .toChecksumValidatingBody(context.protocolResponse.headers[checksumHeader]!!),
        )
    }
}

public class ChecksumMismatchException(message: String?) : ClientException(message)

/**
 * An [SdkSource] which validates the underlying [hashingSource]'s checksum against an [expectedChecksum].
 */
private class ChecksumValidatingSource(
    private val expectedChecksum: String,
    private val hashingSource: HashingSource,
) : SdkSource by hashingSource {
    override fun read(sink: SdkBuffer, limit: Long): Long = hashingSource.read(sink, limit).also {
        if (it == -1L) {
            validateAndThrow(expectedChecksum, hashingSource.digest().encodeBase64String())
        }
    }
}

/**
 * An [SdkByteReadChannel] which validates the underlying [hashingChan]'s checksum against an [expectedChecksum].
 */
private class ChecksumValidatingByteReadChannel(
    private val expectedChecksum: String,
    private val hashingChan: HashingByteReadChannel,
) : SdkByteReadChannel by hashingChan {
    override suspend fun read(sink: SdkBuffer, limit: Long): Long = hashingChan.read(sink, limit).also {
        if (it == -1L) {
            validateAndThrow(expectedChecksum, hashingChan.digest().encodeBase64String())
        }
    }
}

/**
 * Convert an [HttpBody] with an underlying [HashingSource] or [HashingByteReadChannel]
 * to a [ChecksumValidatingSource] or [ChecksumValidatingByteReadChannel], respectively.
 */
private fun HttpBody.toChecksumValidatingBody(expectedChecksum: String) = when (this) {
    is HttpBody.SourceContent -> ChecksumValidatingSource(expectedChecksum, (readFrom() as HashingSource)).toHttpBody(contentLength)
    is HttpBody.ChannelContent -> ChecksumValidatingByteReadChannel(expectedChecksum, (readFrom() as HashingByteReadChannel)).toHttpBody(contentLength)
    else -> throw ClientException("HttpBody type is not supported")
}

/**
 * Validate the checksums, throwing [ChecksumMismatchException] if they do not match
 */
private fun validateAndThrow(expected: String, actual: String) {
    if (expected != actual) {
        throw ChecksumMismatchException("Checksum mismatch. Expected $expected but was $actual")
    }
}
