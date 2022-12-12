/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.auth.HttpSigner
import aws.smithy.kotlin.runtime.http.operation.SdkHttpRequest
import aws.smithy.kotlin.runtime.io.Handler

/**
 * HTTP middleware handler that signs requests
 * @param inner handler to wrap
 * @param signer the signer to use to sign the request with
 */
internal class HttpAuthMiddleware<O>(
    private val inner: Handler<SdkHttpRequest, O>,
    private val signer: HttpSigner,
) : Handler<SdkHttpRequest, O> {
    override suspend fun call(request: SdkHttpRequest): O {
        signer.sign(request.context, request.subject)
        return inner.call(request)
    }
}
