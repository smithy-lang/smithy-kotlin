/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest

/**
 * An interceptor that validates the request body has a known content length.
 * This is used for operations with streaming input members marked with the `@requiresLength` trait.
 */
@InternalApi
public class RequiresLengthInterceptor : HttpInterceptor {
    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        val req = context.protocolRequest
        if (req.body.contentLength == null) {
            throw ClientException("Operation requires a content length but the request body size is unknown. Provide a ByteStream with a known contentLength.")
        }
        return req
    }
}
