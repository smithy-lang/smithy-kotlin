/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.hashing.toHashFunction
import aws.smithy.kotlin.runtime.http.isSuccess
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.response.HttpCall
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
        // The expected response checksum from the response's headers
        public val ExpectedResponseChecksum: AttributeKey<String> = AttributeKey("ExpectedResponseChecksum")

        // The actual response checksum
        public val ResponseChecksum: AttributeKey<Deferred<String>> = AttributeKey("ResponseChecksum")

        // The name of the checksum header which was validated. If `null`, validation was not performed.
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

        // let the user know which checksum will be validated
        logger.debug { "Validating checksum from $checksumHeader" }
        request.context[ChecksumHeaderValidated] = checksumHeader

        val checksumAlgorithm = checksumHeader.removePrefix("x-amz-checksum-").toHashFunction() ?: throw ClientException("could not parse checksum algorithm from header $checksumHeader")

        val deferredChecksum = CompletableDeferred<String>()

        // Wrap the response body in a hashing body
        call = call.copy(
            response = call.response.copy(
                body = call.response.body.toHashingBody(checksumAlgorithm, call.response.body.contentLength, deferredChecksum)
            ),
        )

        request.context[ExpectedResponseChecksum] = call.response.headers[checksumHeader]!!
        request.context[ResponseChecksum] = deferredChecksum

        return call
    }
}
