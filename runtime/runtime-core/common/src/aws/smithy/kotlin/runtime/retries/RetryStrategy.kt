/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy

/**
 * A strategy for trying a block of code one or more times.
 */
public interface RetryStrategy {
    /**
     * Common retry strategy options
     */
    public val options: RetryOptions

    /**
     * Retry the given block of code until it's successful. Note this method throws exceptions for non-successful
     * outcomes from retrying.
     * @param policy A [RetryPolicy] that can be used to evaluate the outcome of each retry attempt.
     * @param block The block of code to retry.
     * @return The successful [Outcome] of the final retry attempt.
     */
    public suspend fun <R> retry(policy: RetryPolicy<R>, block: suspend () -> R): Outcome<R>
}

public interface RetryOptions {
    /**
     * Maximum retry attempts allowed by a strategy
     */
    public val maxAttempts: Int?
        get() = null
}
