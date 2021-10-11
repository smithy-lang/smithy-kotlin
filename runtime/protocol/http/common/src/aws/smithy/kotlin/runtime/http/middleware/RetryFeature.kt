/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.Feature
import aws.smithy.kotlin.runtime.http.FeatureKey
import aws.smithy.kotlin.runtime.http.HttpClientFeatureFactory
import aws.smithy.kotlin.runtime.http.operation.SdkHttpOperation
import aws.smithy.kotlin.runtime.http.operation.deepCopy
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.retries.RetryPolicy
import aws.smithy.kotlin.runtime.retries.RetryStrategy

class RetryFeature(private val strategy: RetryStrategy, private val policy: RetryPolicy<Any?>) : Feature {
    class Config {
        var strategy: RetryStrategy? = null
        var policy: RetryPolicy<Any?>? = null
    }

    companion object Feature : HttpClientFeatureFactory<Config, RetryFeature> {
        override val key: FeatureKey<RetryFeature> = FeatureKey("RetryFeature")

        override fun create(block: Config.() -> Unit): RetryFeature {
            val config = Config().apply(block)
            val strategy = requireNotNull(config.strategy) { "strategy is required" }
            val policy = requireNotNull(config.policy) { "policy is required" }
            return RetryFeature(strategy, policy)
        }
    }

    override fun <I, O> install(operation: SdkHttpOperation<I, O>) {
        operation.execution.finalize.intercept { req, next ->
            if (req.subject.isRetryable) {
                strategy.retry(policy) {
                    // Deep copy the request because later middlewares (e.g., signing) mutate it
                    val reqCopy = req.deepCopy()

                    // Reset bodies back to beginning (mainly for streaming bodies)
                    reqCopy.subject.body.reset()

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
    get() = body.isReplayable
