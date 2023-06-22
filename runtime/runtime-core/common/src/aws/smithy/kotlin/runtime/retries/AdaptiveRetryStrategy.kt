/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.retries.delay.*
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.util.DslBuilderProperty
import aws.smithy.kotlin.runtime.util.DslFactory

/**
 * Implements a retry strategy with exponential backoff, a token bucket for limiting retries, and a client-side
 * rate limiter for achieving the ideal request rate. Note that the backoff delayer, token bucket, and rate limiter all
 * work independently of each other. Any of the three may delay retries (and the rate limiter may delay the initial try
 * as well).
 *
 * **Note**: The adaptive retry strategy is an advanced mode. It is not recommended for typical use cases. In most
 * cases, [StandardRetryStrategy] is the preferred retry mode.
 * @param config The configuration for this retry strategy
 */
public class AdaptiveRetryStrategy(override val config: Config = Config.Default) : StandardRetryStrategy(config) {
    public companion object : DslFactory<Config.Builder, AdaptiveRetryStrategy> {
        override fun invoke(block: Config.Builder.() -> Unit): AdaptiveRetryStrategy =
            AdaptiveRetryStrategy(Config(Config.Builder().apply(block)))
    }

    override suspend fun beforeInitialTry() {
        super.beforeInitialTry()
        config.rateLimiter.acquire(1)
    }

    override suspend fun <R> afterTry(
        attempt: Int,
        callResult: Result<R>,
        evaluation: RetryDirective,
        policy: RetryPolicy<R>,
    ) {
        super.afterTry(attempt, callResult, evaluation, policy)
        val errorType = (evaluation as? RetryDirective.RetryError)?.reason
        config.rateLimiter.update(errorType)
    }

    override suspend fun <R> beforeRetry(
        attempt: Int,
        callResult: Result<R>,
        evaluation: RetryDirective,
        policy: RetryPolicy<R>,
    ) {
        super.beforeRetry(attempt, callResult, evaluation, policy)
        config.rateLimiter.acquire(1)
    }

    /**
     * Configuration parameters for an adaptive retry strategy
     */
    public class Config(builder: Builder) : StandardRetryStrategy.Config(builder) {
        public companion object {
            /**
             * The default configuration
             */
            public val Default: Config = Config(Builder())
        }

        /**
         * The rate limiter which may delay initial tries or retries. Defaults to an [AdaptiveRateLimiter].
         */
        public val rateLimiter: RateLimiter = builder.rateLimiterProperty.supply()

        public class Builder : StandardRetryStrategy.Config.Builder() {
            internal val rateLimiterProperty = DslBuilderProperty<RateLimiter.Config.Builder, RateLimiter>(
                AdaptiveRateLimiter,
                { config.toBuilderApplicator() },
            )

            /**
             * The rate limiter which may delay initial tries or retries. Defaults to an [AdaptiveRateLimiter].
             */
            public var rateLimiter: RateLimiter? by rateLimiterProperty::instance

            public fun rateLimiter(block: AdaptiveRateLimiter.Config.Builder.() -> Unit) {
                rateLimiterProperty.dsl(AdaptiveRateLimiter, block)
            }

            public fun <B : RateLimiter.Config.Builder, C : RateLimiter> rateLimiter(
                factory: DslFactory<B, C>,
                block: B.() -> Unit,
            ) {
                rateLimiterProperty.dsl(factory, block)
            }
        }
    }
}
