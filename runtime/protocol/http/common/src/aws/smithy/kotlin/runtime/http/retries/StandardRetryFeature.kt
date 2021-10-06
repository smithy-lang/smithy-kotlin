/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.retries

import aws.smithy.kotlin.runtime.http.Feature
import aws.smithy.kotlin.runtime.http.FeatureKey
import aws.smithy.kotlin.runtime.http.HttpClientFeatureFactory
import aws.smithy.kotlin.runtime.http.operation.SdkHttpOperation
import aws.smithy.kotlin.runtime.retries.RetryPolicy
import aws.smithy.kotlin.runtime.retries.RetryStrategy

class StandardRetryFeature(private val strategy: RetryStrategy, private val policy: RetryPolicy<Any?>) : Feature {
    class Config {
        var strategy: RetryStrategy? = null
        var policy: RetryPolicy<Any?>? = null
    }

    companion object Feature : HttpClientFeatureFactory<Config, StandardRetryFeature> {
        override val key: FeatureKey<StandardRetryFeature> = FeatureKey("StandardRetryFeature")

        override fun create(block: Config.() -> Unit): StandardRetryFeature {
            val config = Config().apply(block)
            val strategy = requireNotNull(config.strategy) { "strategy is required" }
            val policy = requireNotNull(config.policy) { "policy is required" }
            return StandardRetryFeature(strategy, policy)
        }
    }

    override fun <I, O> install(operation: SdkHttpOperation<I, O>) {
        operation.execution.finalize.intercept { req, next ->
            strategy.retry(policy) { next.call(req) }
        }
    }
}
