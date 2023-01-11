/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.hashing.toHashFunction
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.isSuccess
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.toCompletingBody
import aws.smithy.kotlin.runtime.http.toHashingBody
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.util.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlin.coroutines.coroutineContext

// The priority to validate response checksums, if multiple are present
internal val CHECKSUM_HEADER_VALIDATION_PRIORITY_LIST: List<String> = listOf(
    "x-amz-checksum-crc32c",
    "x-amz-checksum-crc32",
    "x-amz-checksum-sha1",
    "x-amz-checksum-sha256",
)

internal val MULTIPART_CHECKSUM_HEADER_REGEX = Regex("x-amz-checksum-[a-zA-Z0-9]+-[0-9]+")

/**
 * Validate a flexible checksums response.
 *
 * Wraps the response in a hashing body, calculating the checksum as the response is streamed to the user.
 * The checksum is validated after the user has consumed the entire body.
 * Users can see which checksum was validated by referencing the `ResponseChecksumValidated` execution context variable.
 */
@InternalApi
public class FlexibleChecksumsResponseMiddleware : ReceiveMiddleware {

    public companion object {
        public val ResponseChecksum: AttributeKey<Deferred<String>> = AttributeKey("ResponseChecksum")
        public val ExpectedResponseChecksum: AttributeKey<String> = AttributeKey("ExpectedResponseChecksum")

        public val ChecksumHeaderValidated: AttributeKey<String> = AttributeKey("ChecksumHeaderValidated")
    }

    public override suspend fun <H : Handler<SdkHttpRequest, HttpCall>> handle(request: SdkHttpRequest, next: H): HttpCall {
        val logger = coroutineContext.getLogger<FlexibleChecksumsResponseMiddleware>()

        var call = next.call(request)
        if (!call.response.status.isSuccess()) {
            logger.error { "Call not successful" }
            return call
        }

        val checksumHeader = CHECKSUM_HEADER_VALIDATION_PRIORITY_LIST
            .firstOrNull { call.response.headers.contains(it) } ?: run {
            logger.warn { "User requested checksum validation, but the response headers did not contain any valid checksums" }
            return call
        }

        if (MULTIPART_CHECKSUM_HEADER_REGEX.matches(checksumHeader)) {
            logger.info { "Skipping validation of multipart response checksum $checksumHeader" }
            return call
        }

        // let the user know which checksum will be validated
        logger.debug { "Validating checksum in $checksumHeader" }
        request.context[ChecksumHeaderValidated] = checksumHeader

        val checksumAlgorithm = checksumHeader.removePrefix("x-amz-checksum-").toHashFunction() ?: throw ClientException("could not parse checksum algorithm from header $checksumHeader")

        val deferredChecksum = CompletableDeferred<String>()

        // Wrap the response body in a hashing body
        logger.debug { "Setting hashing body" }
        call = call.copy(
            response = call.response.copy(
                body = call.response.body
                    .toHashingBody(checksumAlgorithm, call.response.body.contentLength)
                    .toCompletingBody(deferredChecksum, call.response.body.contentLength),
            ),
        )

        request.context[ExpectedResponseChecksum] = call.response.headers[checksumHeader]!!
        request.context[ResponseChecksum] =  deferredChecksum

        return call
    }

    /**
     * Returns the Base64 encoded checksum of an HttpBody
     * To use this, the HttpBody's underlying data source *must* be either a [HashingSource] or [HashingByteReadChannel],
     * which means the HttpBody must also be either an [HttpBody.SourceContent] or [HttpBody.ChannelContent]. An exception
     * will be thrown otherwise.
     * @return the Base64 encoded checksum of the HttpBody
     */
    public val HttpBody.checksum: String get() = when (this) {
        is HttpBody.SourceContent -> { (readFrom() as HashingSource).digest().encodeBase64String() }
        is HttpBody.ChannelContent -> { (readFrom() as HashingByteReadChannel).digest().encodeBase64String() }
        else -> throw ClientException("HttpBody type is not supported")
    }
}
