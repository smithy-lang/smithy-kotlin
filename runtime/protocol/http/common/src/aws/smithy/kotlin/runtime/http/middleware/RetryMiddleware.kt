/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.operation.deepCopy
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.io.middleware.Middleware
import aws.smithy.kotlin.runtime.retries.RetryStrategy
import aws.smithy.kotlin.runtime.retries.getOrThrow
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.tracing.TraceSpan
import aws.smithy.kotlin.runtime.tracing.debug
import aws.smithy.kotlin.runtime.tracing.traceSpan
import aws.smithy.kotlin.runtime.tracing.withChildTraceSpan
import kotlin.coroutines.coroutineContext

/**
 * Retry requests with the given strategy and policy
 * @param strategy the [RetryStrategy] to retry failed requests with
 * @param policy the [RetryPolicy] used to determine when to retry
 */
internal class RetryMiddleware<O>(
    private val strategy: RetryStrategy,
    private val policy: RetryPolicy<O>,
) : Middleware<SdkHttpRequest, O> {
    override suspend fun <H : Handler<SdkHttpRequest, O>> handle(request: SdkHttpRequest, next: H): O =
        if (request.subject.isRetryable) {
            var attempt = 1

            // FIXME this is the wrong span because we want the fresh one from inside each attempt but there's no way to
            // wire that through without changing the `RetryPolicy` interface
            val wrappedPolicy = PolicyLogger(policy, coroutineContext.traceSpan)

            val outcome = strategy.retry(wrappedPolicy) {
                coroutineContext.withChildTraceSpan("Attempt-$attempt") {
                    if (attempt > 1) {
                        coroutineContext.debug<RetryMiddleware<*>> { "retrying request, attempt $attempt" }
                    }

                    // Deep copy the request because later middlewares (e.g., signing) mutate it
                    val requestCopy = request.deepCopy()

                    attempt++
                    next.call(requestCopy)
                }
            }
            outcome.getOrThrow()
        } else {
            // Create a child span even though we won't retry
            coroutineContext.withChildTraceSpan("Non-retryable attempt") {
                next.call(request)
            }
        }
}

/**
 * Wrapper around [policy] that logs termination decisions
 */
private class PolicyLogger<O>(
    private val policy: RetryPolicy<O>,
    private val traceSpan: TraceSpan,
) : RetryPolicy<O> {
    override fun evaluate(result: Result<O>): RetryDirective = policy.evaluate(result).also {
        if (it is RetryDirective.TerminateAndFail) {
            traceSpan.debug<RetryMiddleware<*>> { "request failed with non-retryable error" }
        }
    }
}

/**
 * Indicates whether this HTTP request could be retried. Some requests with streaming bodies are unsuitable for
 * retries.
 */
private val HttpRequestBuilder.isRetryable: Boolean
    get() = !body.isOneShot
