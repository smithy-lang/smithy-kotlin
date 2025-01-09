/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.businessmetrics.emitBusinessMetric
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.io.rollingHash
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext

/**
 * Handles checksum request calculation from the `httpChecksumRequired` trait.
 */
@InternalApi
public class HttpChecksumRequiredInterceptor : AbstractChecksumInterceptor() {
    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        if (context.defaultChecksumAlgorithmName == null) {
            // Don't calculate checksum
            return context.protocolRequest
        }

        val checksumAlgorithmName = context.defaultChecksumAlgorithmName!!
        val checksumAlgorithm = checksumAlgorithmName.toHashFunctionOrThrow()

        val logger = coroutineContext.logger<HttpChecksumRequiredInterceptor>()

        if (context.protocolRequest.body.isEligibleForAwsChunkedStreaming) { // Handle checksum calculation here
            logger.debug { "Calculating checksum during transmission using: ${checksumAlgorithm::class.simpleName}" }

            val request = context.protocolRequest.toBuilder()
            val deferredChecksum = CompletableDeferred<String>(context.executionContext.coroutineContext.job)
            val checksumHeader = checksumAlgorithm.resolveChecksumAlgorithmHeaderName()

            request.body = request.body
                .toHashingBody(checksumAlgorithm, request.body.contentLength)
                .toCompletingBody(deferredChecksum)

            request.headers.append("x-amz-trailer", checksumHeader)
            request.trailingHeaders.append(checksumHeader, deferredChecksum)

            context.executionContext.emitBusinessMetric(checksumAlgorithm.toBusinessMetric())

            return request.build()
        } else { // Delegate checksum calculation to super class, calculateChecksum, and applyChecksum
            return super.modifyBeforeSigning(context)
        }
    }

    public override suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String? {
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

    public override fun applyChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>, checksum: String): HttpRequest {
        val checksumAlgorithmName = context.defaultChecksumAlgorithmName!!
        val checksumHeader = checksumAlgorithmName.resolveChecksumAlgorithmHeaderName()
        val request = context.protocolRequest.toBuilder()

        request.header(checksumHeader, checksum)
        return request.build()
    }
}
