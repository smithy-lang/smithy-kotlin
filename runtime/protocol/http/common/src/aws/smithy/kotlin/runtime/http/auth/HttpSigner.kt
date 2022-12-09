/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Represents a component capable of signing an HTTP request
 */
@InternalApi
public interface HttpSigner {
    public companion object {
        /**
         * A no-op signer that does nothing with the request
         */
        public val NONE: HttpSigner = object : HttpSigner {
            override suspend fun sign(context: ExecutionContext, request: HttpRequestBuilder) { }
        }
    }

    /**
     * Sign the provided HTTP request (e.g. add AWS SigV4 headers, Bearer token header, etc)
     */
    public suspend fun sign(context: ExecutionContext, request: HttpRequestBuilder)
}
