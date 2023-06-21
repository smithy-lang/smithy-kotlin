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
 * @param config The configuration for this retry strategy
 */
public class AdaptiveRetryStrategy(override val config: Config = Config.Default) : StandardRetryStrategy(config) {
    public companion object : DslFactory<Config.Builder, AdaptiveRetryStrategy> {
        override fun invoke(block: Config.Builder.() -> Unit): AdaptiveRetryStrategy =
            AdaptiveRetryStrategy(Config(Config.Builder().apply(block)))
    }

    override suspend fun beforeInitialTry() {
        super.beforeInitialTry()
        config.clientRateLimiter.acquire(1)
    }

    override suspend fun <R> afterTry(
        attempt: Int,
        callResult: Result<R>,
        evaluation: RetryDirective,
        policy: RetryPolicy<R>,
    ) {
        super.afterTry(attempt, callResult, evaluation, policy)
        val errorType = (evaluation as? RetryDirective.RetryError)?.reason
        config.clientRateLimiter.update(errorType)
    }

    override suspend fun <R> beforeRetry(
        attempt: Int,
        callResult: Result<R>,
        evaluation: RetryDirective,
        policy: RetryPolicy<R>,
    ) {
        super.beforeRetry(attempt, callResult, evaluation, policy)
        config.clientRateLimiter.acquire(1)
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
         * The rate limiter which may delay initial tries or retries. Defaults to an [AdaptiveClientRateLimiter].
         */
        public val clientRateLimiter: ClientRateLimiter = builder.clientRateLimiterProperty.supply()

        public class Builder : StandardRetryStrategy.Config.Builder() {
            internal val clientRateLimiterProperty = DslBuilderProperty<ClientRateLimiter.Config.Builder, ClientRateLimiter>(
                AdaptiveClientRateLimiter,
                { config.toBuilderApplicator() },
            )

            /**
             * The rate limiter which may delay initial tries or retries. Defaults to an [AdaptiveClientRateLimiter].
             */
            public var clientRateLimiter: ClientRateLimiter? by clientRateLimiterProperty::instance

            public fun clientRateLimiter(block: AdaptiveClientRateLimiter.Config.Builder.() -> Unit) {
                clientRateLimiterProperty.dsl(AdaptiveClientRateLimiter, block)
            }

            public fun <B : ClientRateLimiter.Config.Builder, C : ClientRateLimiter> clientRateLimiter(
                factory: DslFactory<B, C>,
                block: B.() -> Unit,
            ) {
                clientRateLimiterProperty.dsl(factory, block)
            }
        }
    }
}
