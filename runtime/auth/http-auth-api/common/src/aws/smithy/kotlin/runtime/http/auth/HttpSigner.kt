/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.operation.ExecutionContext

/**
 * Represents a component capable of signing an HTTP request
 */
public interface HttpSigner {
    /**
     * Sign the provided HTTP request (e.g. add AWS SigV4 headers, Bearer token header, etc)
     */
    public suspend fun sign(signingRequest: SignHttpRequest)
}

/**
 * Container for signing request parameters/config
 */
public data class SignHttpRequest(
    val context: ExecutionContext,
    val httpRequest: HttpRequestBuilder,
    val identity: Identity,
)
