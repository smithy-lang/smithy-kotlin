/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.operation.deepCopy
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.retries.RetryPolicy
import aws.smithy.kotlin.runtime.retries.RetryStrategy

class RetryFeature<I, O>(
    private val strategy: RetryStrategy,
    private val policy: RetryPolicy<Any?>
) : MutateMiddleware<O>, AutoInstall<I, O> {

    override fun install(op: SdkHttpOperation<I, O>) {
        op.execution.finalize.register(this)
    }

    override suspend fun <H : Handler<SdkHttpRequest, O>> handle(request: SdkHttpRequest, next: H): O =
        if (request.subject.isRetryable) {
            strategy.retry(policy) {
                // Deep copy the requestuest because later middlewares (e.g., signing) mutate it
                val requestCopy = request.deepCopy()

                when (val body = requestCopy.subject.body) {
                    // Reset streaming bodies back to beginning
                    is HttpBody.Streaming -> body.reset()
                }

                next.call(requestCopy)
            }
        } else {
            next.call(request)
        }
}

/**
 * Indicates whether this HTTP request could be retried. Some requests with streaming bodies are unsuitable for
 * retries.
 */
val HttpRequestBuilder.isRetryable: Boolean
    get() = when (val body = this.body) {
        is HttpBody.Empty, is HttpBody.Bytes -> true
        is HttpBody.Streaming -> body.isReplayable
    }
