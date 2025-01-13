/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.io.rollingHash
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import kotlin.coroutines.coroutineContext

/**
 * Handles checksum request calculation from the `httpChecksumRequired` trait.
 */
@InternalApi
public class HttpChecksumRequiredInterceptor : CachingChecksumInterceptor() {
    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        if (context.defaultChecksumAlgorithmName == null) {
            // Don't calculate checksum
            return context.protocolRequest
        }

        val checksumAlgorithmName = context.defaultChecksumAlgorithmName!!
        val checksumAlgorithm = checksumAlgorithmName.toHashFunctionOrThrow()

        return if (context.protocolRequest.body.isEligibleForAwsChunkedStreaming) {
            coroutineContext.logger<HttpChecksumRequiredInterceptor>().debug {
                "Calculating checksum during transmission using: ${checksumAlgorithm::class.simpleName}"
            }
            calculateAwsChunkedStreamingChecksum(context, checksumAlgorithm)
        } else {
            if (context.protocolRequest.body is HttpBody.Bytes) {
                // Cache checksum
                super.modifyBeforeSigning(context)
            } else {
                val checksum = calculateHttpChecksumRequiredChecksum(context)
                applyHttpChecksumRequiredChecksum(context, checksum)
            }
        }
    }

    /**
     * Calculates a checksum based on the requirements and limitations of [HttpChecksumRequiredInterceptor]
     */
    private suspend fun calculateHttpChecksumRequiredChecksum(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): String {
        val req = context.protocolRequest.toBuilder()
        val checksumAlgorithmName = context.defaultChecksumAlgorithmName!!
        val checksumAlgorithm = checksumAlgorithmName.toHashFunctionOrThrow()

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

    public override suspend fun calculateChecksum(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): String? =
        calculateHttpChecksumRequiredChecksum(context)

    /**
     * Applies a checksum based on the requirements and limitations of [HttpChecksumRequiredInterceptor]
     */
    private fun applyHttpChecksumRequiredChecksum(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
        checksum: String,
    ): HttpRequest {
        val checksumAlgorithmName = context.defaultChecksumAlgorithmName!!
        val checksumHeader = checksumAlgorithmName.resolveChecksumAlgorithmHeaderName()
        val request = context.protocolRequest.toBuilder()

        request.header(checksumHeader, checksum)
        return request.build()
    }

    public override fun applyChecksum(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
        checksum: String,
    ): HttpRequest =
        applyHttpChecksumRequiredChecksum(context, checksum)
}
