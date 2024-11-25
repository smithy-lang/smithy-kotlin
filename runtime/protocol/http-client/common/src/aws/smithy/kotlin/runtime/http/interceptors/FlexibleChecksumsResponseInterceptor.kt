/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.client.config.ChecksumConfigOption
import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.hashing.toHashFunction
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.copy
import aws.smithy.kotlin.runtime.http.toHashingBody
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.telemetry.logging.debug
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
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
 * If it's a streaming response, it wraps the response in a hashing body, calculating the checksum as the response is
 * streamed to the user. The checksum is validated after the user has consumed the entire body using a checksum validating body.
 * Otherwise, the checksum if calculated all at once.
 *
 * Users can check which checksum was validated by referencing the `ResponseChecksumValidated` execution context variable.
 *
 * @param responseValidation Flag indicating if the checksum validation is mandatory.
 * @param responseChecksumValidation Configuration option that determines when checksum validation should be done.
 */
@InternalApi
public class FlexibleChecksumsResponseInterceptor(
    private val responseValidation: Boolean,
    private val responseChecksumValidation: ChecksumConfigOption?,
) : HttpInterceptor {
    @InternalApi
    public companion object {
        // The name of the checksum header which was validated. If `null`, validation was not performed.
        public val ChecksumHeaderValidated: AttributeKey<String> = AttributeKey("ChecksumHeaderValidated")
    }

    override suspend fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
        val logger = coroutineContext.logger<FlexibleChecksumsResponseInterceptor>()

        val forcedToVerifyChecksum = responseValidation || responseChecksumValidation == ChecksumConfigOption.WHEN_SUPPORTED
        if (!forcedToVerifyChecksum) return context.protocolResponse

        val checksumHeader = CHECKSUM_HEADER_VALIDATION_PRIORITY_LIST
            .firstOrNull { context.protocolResponse.headers.contains(it) } ?: run {
            logger.warn { "User requested checksum validation, but the response headers did not contain any valid checksums" }
            return context.protocolResponse
        }
        val checksumAlgorithm = checksumHeader.removePrefix("x-amz-checksum-").toHashFunction() ?: throw ClientException("Could not parse checksum algorithm from header $checksumHeader")
        val checksumValue = context.protocolResponse.headers[checksumHeader]!!

        if (checksumValue.isCompositeChecksum()) {
            logger.debug { "Service returned composite checksum. Skipping validation." }
            return context.protocolResponse
        }

        logger.debug { "Validating checksum from $checksumHeader" }
        context.executionContext[ChecksumHeaderValidated] = checksumHeader

        if (context.protocolResponse.body is HttpBody.Bytes) {
            checksumAlgorithm.update(
                context.protocolResponse.body.readAll() ?: byteArrayOf(),
            )
            validateAndThrow(
                checksumValue,
                checksumAlgorithm.digest().encodeBase64String(),
            )
            return context.protocolResponse
        } else {
            // Wrap the response body in a hashing body
            return context.protocolResponse.copy(
                body = context.protocolResponse.body
                    .toHashingBody(checksumAlgorithm, context.protocolResponse.body.contentLength)
                    .toChecksumValidatingBody(checksumValue),
            )
        }
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

/**
 * Verifies if a checksum is composite.
 * Composite checksums are used for multipart uploads.
 */
private fun String.isCompositeChecksum(): Boolean {
    // Ends with "-#" where "#" is a number between 1-1000
    val regex = Regex("-([1-9][0-9]{0,2}|1000)$")
    return regex.containsMatchIn(this)
}
