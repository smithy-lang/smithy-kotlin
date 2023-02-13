/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder

/**
 * An interceptor that adds an HTTP `Expect: 100-continue` header to requests with bodies at a certain length threshold.
 * Bodies with an unset `contentLength` will get the continue header added regardless of length.
 * @param thresholdLengthBytes The body length (in bytes) at which a continue header will be set. Bodies under this
 * length will not get a continue header.
 */
@InternalApi
public class ContinueInterceptor(public val thresholdLengthBytes: Long) : HttpInterceptor {
    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        val req = context.protocolRequest

        return if ((req.body.contentLength ?: Long.MAX_VALUE) >= thresholdLengthBytes) {
            req
                .toBuilder()
                .apply { header("Expect", "100-continue") }
                .build()
        } else {
            req
        }
    }
}
