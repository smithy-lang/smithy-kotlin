/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors


import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest

public abstract class AbstractChecksumInterceptor: HttpInterceptor {
    private var cachedChecksum: String? = null

    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        cachedChecksum ?: calculateChecksum(context).also { cachedChecksum = it }
        return applyChecksum(context, cachedChecksum)
    }

    public abstract suspend fun calculateChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): String?

    public abstract fun applyChecksum(context: ProtocolRequestInterceptorContext<Any, HttpRequest>, checksum: String?): HttpRequest
}
