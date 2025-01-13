/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest

/**
 * Enables inheriting [HttpInterceptor]s to use checksums caching
 */
@InternalApi
public abstract class CachingChecksumInterceptor : HttpInterceptor {
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
