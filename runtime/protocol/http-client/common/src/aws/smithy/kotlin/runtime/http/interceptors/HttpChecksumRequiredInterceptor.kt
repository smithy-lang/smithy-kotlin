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
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String

/**
 * Handles checksum request calculation from the `httpChecksumRequired` trait.
 */
@InternalApi
public class HttpChecksumRequiredInterceptor : AbstractChecksumInterceptor() {
    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest =
        if (context.defaultChecksumAlgorithmName == null) {
            // Don't calculate checksum
            context.protocolRequest
        } else {
            // Delegate checksum calculation to super class, calculateChecksum, and applyChecksum
            super.modifyBeforeSigning(context)
        }

    public override suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String? {
        val checksumAlgorithmName = context.defaultChecksumAlgorithmName!!
        val checksumAlgorithm = checksumAlgorithmName.toHashFunctionOrThrow()

        return when (val body = context.protocolRequest.body) {
            is HttpBody.Bytes -> {
                checksumAlgorithm.update(
                    body.readAll() ?: byteArrayOf(),
                )
                checksumAlgorithm.digest().encodeBase64String()
            }
            else -> null // TODO: Support other body types
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
