/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.RetryContext
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType

/**
 * A [DelayProvider] that can adjust backoff based on retry context such as the error type, service name,
 * and server-specified retry-after duration.
 */
public interface RetryAwareDelayProvider : DelayProvider {
    /**
     * Delegates to [backoff] with [RetryErrorType.ServerSide] and no service name or retry-after value.
     */
    override suspend fun backoff(attempt: Int) {
        backoff(attempt, RetryErrorType.ServerSide)
    }

    /**
     * Delays for an appropriate amount of time after the given attempt number, taking into account the type of error
     * and the service name. If a [RetryContext] is present in the current coroutine context with a non-null
     * [RetryContext.retryAfter], implementations should use it to adjust the delay.
     * @param attempt The ordinal index of the attempt.
     * @param errorType The type of error that triggered the retry.
     * @param serviceName The optional service name, used for service-specific backoff (e.g., DynamoDB).
     */
    public suspend fun backoff(
        attempt: Int,
        errorType: RetryErrorType,
        serviceName: String? = null,
    )
}
