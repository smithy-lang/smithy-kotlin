/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.Feature
import aws.smithy.kotlin.runtime.http.FeatureKey
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpClientFeatureFactory
import aws.smithy.kotlin.runtime.http.operation.SdkHttpOperation
import aws.smithy.kotlin.runtime.http.operation.deepCopy
import aws.smithy.kotlin.runtime.http.operation.getLogger
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.logging.Logger
import aws.smithy.kotlin.runtime.retries.RetryDirective
import aws.smithy.kotlin.runtime.retries.RetryPolicy
import aws.smithy.kotlin.runtime.retries.RetryStrategy
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Retry requests with the given strategy and policy
 * @param strategy the [RetryStrategy] to retry failed requests with
 * @param policy the [RetryPolicy] used to determine when to retry
 */
@InternalApi
class Retry(
    private val strategy: RetryStrategy,
    private val policy: RetryPolicy<Any?>
) : Feature {

    class Config {
        var strategy: RetryStrategy? = null
        var policy: RetryPolicy<Any?>? = null
    }

    companion object Feature : HttpClientFeatureFactory<Config, Retry> {
        override val key: FeatureKey<Retry> = FeatureKey("Retry")

        override fun create(block: Config.() -> Unit): Retry {
            val config = Config().apply(block)
            val strategy = requireNotNull(config.strategy) { "strategy is required" }
            val policy = requireNotNull(config.policy) { "policy is required" }
            return Retry(strategy, policy)
        }
    }

    /**
     * Wrapper around [policy] that logs termination decisions
     */
    private class PolicyLogger(
        private val policy: RetryPolicy<Any?>,
        private val logger: Logger,
    ) : RetryPolicy<Any?> {
        override fun evaluate(result: Result<Any?>): RetryDirective = policy.evaluate(result).also {
            if (it is RetryDirective.TerminateAndFail) {
                logger.debug { "request failed with non-retryable error" }
            }
        }
    }

    override fun <I, O> install(operation: SdkHttpOperation<I, O>) {
        operation.execution.finalize.intercept { req, next ->
            if (req.subject.isRetryable) {
                var attempt = 1
                val logger = req.context.getLogger("RetryFeature")
                val wrappedPolicy = PolicyLogger(policy, logger)

                strategy.retry(wrappedPolicy) {
                    if (attempt > 1) {
                        logger.debug { "retrying request, attempt $attempt" }
                    }

                    // Deep copy the request because later middlewares (e.g., signing) mutate it
                    val reqCopy = req.deepCopy()

                    when (val body = reqCopy.subject.body) {
                        // Reset streaming bodies back to beginning
                        is HttpBody.Streaming -> body.reset()
                    }

                    attempt++

                    next.call(reqCopy)
                }
            } else {
                next.call(req)
            }
        }
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
