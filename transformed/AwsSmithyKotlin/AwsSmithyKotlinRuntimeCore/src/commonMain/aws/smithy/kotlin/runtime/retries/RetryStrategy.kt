/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy

@DslMarker
public annotation class RetryStrategyConfigDsl

/**
 * A strategy for trying a block of code one or more times.
 */
public interface RetryStrategy {
    /**
     * The configured parameters for this strategy
     */
    public val config: Config

    /**
     * Retry the given block of code until it's successful. Note this method throws exceptions for non-successful
     * outcomes from retrying.
     * @param policy A [RetryPolicy] that can be used to evaluate the outcome of each retry attempt.
     * @param block The block of code to retry.
     * @return The successful [Outcome] of the final retry attempt.
     */
    public suspend fun <R> retry(policy: RetryPolicy<R>, block: suspend () -> R): Outcome<R>

    /**
     * Options for configuring a retry strategy
     */
    public interface Config {
        /**
         * Maximum retry attempts allowed by a strategy
         */
        public val maxAttempts: Int

        @InternalApi
        public fun toBuilderApplicator(): Builder.() -> Unit

        @RetryStrategyConfigDsl
        public interface Builder {
            /**
             * Maximum retry attempts allowed by a strategy
             */
            public var maxAttempts: Int
        }
    }
}
