/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest

/**
 * Handles checksum calculation so that checksums will be cached during retry loop
 */
@InternalApi
public abstract class AbstractChecksumInterceptor : HttpInterceptor {
    private var cachedChecksum: String? = null

    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        cachedChecksum = cachedChecksum ?: calculateChecksum(context)

        return if (cachedChecksum != null) {
            applyChecksum(context, cachedChecksum!!)
        } else {
            context.protocolRequest
        }
    }

    public abstract suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String?

    public abstract fun applyChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>, checksum: String): HttpRequest
}

/**
 * @return The default checksum algorithm name, null if default checksums are disabled.
 */
internal fun defaultChecksumAlgorithmName(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String? =
    context.executionContext.getOrNull(HttpOperationContext.DefaultChecksumAlgorithm)
