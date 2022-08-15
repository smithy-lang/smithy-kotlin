/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType

/**
 * A rate-limiting token bucket for use in a client-throttled retry strategy.
 */
public interface RetryTokenBucket {
    /**
     * Acquire a token from the token bucket. This method should be called before the initial retry attempt for a block
     * of code. This method may delay if there are already insufficient tokens in the bucket due to prior retry
     * failures or large numbers of simultaneous requests.
     */
    public suspend fun acquireToken(): RetryToken
}

/**
 * A token from a [RetryTokenBucket]. This token grants the holder the right to attempt a try/retry of a block of code.
 * The token is effectively "borrowed" from the token bucket and must be returned when the retry attempt is completed,
 * either by calling [notifySuccess] or [scheduleRetry].
 *
 */
public interface RetryToken {
    /**
     * Completes this token because retrying has been abandoned.
     */
    public suspend fun notifyFailure()

    /**
     * Completes this token because the previous retry attempt was successful.
     */
    public suspend fun notifySuccess()

    /**
     * Completes this token and requests another one because the previous retry attempt was unsuccessful.
     * @param reason The type of error evaluated from the last retry attempt.
     * @return A new token for a subsequent retry attempt.
     */
    public suspend fun scheduleRetry(reason: RetryErrorType): RetryToken
}

/**
 * Indicates that the token bucket has exhausted its capacity and was configured to throw exceptions (vs delay).
 * @param message A message indicating the failure mode.
 */
public class RetryCapacityExceededException(message: String) : ClientException(message)
