/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType

/**
 * A client-side utility that can limit the rate of transactions. This limiter is usually backed by some set of permits
 * which refill over time. This kind of rate limiting is often used in conjunction with a retry algorithm, especially
 * when it may be desirable to delay the initial attempt of a transaction (instead of just delaying subsequent
 * attempts).
 *
 * Callers should invoke [acquire] with the cost of their transaction prior to executing. If
 * there are not enough permits available currently, the call may delay.
 *
 * Once the transaction is completed, callers should invoke [update] and indicate the type of error (if any) the
 * transaction yielded. The rate limiter may use this information to adjust the number of available permits or the rate
 * of permit renewal.
 */
public interface ClientRateLimiter {
    public val config: Config

    /**
     * Acquire a "permit" to conduct a transaction. If not enough permits are available, this method may delay.
     * @param cost The relative cost of the transaction. Some transactions may take more work or represent a bundle of
     * smaller transactions.
     */
    public suspend fun acquire(cost: Int)

    /**
     * Update the rate limiter with the result of a transaction. The rate limiter may use this information to adjust the
     * number of available permits or the rate at which permits refill.
     * @param errorType The type of error that resulted from the last transaction. A value of `null` indicates the
     * transaction was successful.
     */
    public suspend fun update(errorType: RetryErrorType?)

    public interface Config {
        @InternalApi
        public fun toBuilderApplicator(): Builder.() -> Unit

        public interface Builder
    }
}
