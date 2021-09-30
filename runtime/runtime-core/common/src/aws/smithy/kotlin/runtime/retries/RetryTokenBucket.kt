/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries

/**
 * A rate-limiting token bucket for use in a client-throttled retry strategy.
 */
interface RetryTokenBucket {
    /**
     * Acquire a token from the token bucket. This method should be called before the initial retry attempt for a block
     * of code. This method may delay if there are already insufficient tokens in the bucket due to prior retry
     * failures or large numbers of simultaneous requests.
     */
    suspend fun acquireToken(): RetryToken
}

/**
 * A token from a [RetryTokenBucket]. This token grants the holder the right to attempt a try/retry of a block of code.
 * The token is effectively "borrowed" from the token bucket and must be returned when the retry attempt is completed,
 * either by calling [notifySuccess] or [scheduleRetry].
 *
 */
interface RetryToken {
    /**
     * Completes this token because retrying has been abandoned.
     */
    suspend fun notifyFailure()

    /**
     * Completes this token because the previous retry attempt was successful.
     */
    suspend fun notifySuccess()

    /**
     * Completes this token and requests another one because the previous retry attempt was unsuccessful.
     * @param reason The type of error evaluated from the last retry attempt.
     * @return A new token for a subsequent retry attempt.
     */
    suspend fun scheduleRetry(reason: RetryErrorType): RetryToken
}
