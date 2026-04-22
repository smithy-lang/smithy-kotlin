/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType

/**
 * A [DelayProvider] that can adjust backoff based on retry context such as the error type, service name,
 * and server-specified retry-after duration.
 */
public interface RetryAwareDelayProvider : DelayProvider {
    /**
     * Delays for an appropriate amount of time after the given attempt number, taking into account the type of error,
     * the service name, and an optional server-specified retry-after duration.
     * @param attempt The ordinal index of the attempt.
     * @param errorType The type of error that triggered the retry.
     * @param serviceName The optional service name, used for service-specific backoff (e.g., DynamoDB).
     * @param retryAfterMillis The optional server-specified retry-after duration in milliseconds from the
     * `x-amz-retry-after` response header. When present, the delay is clamped to `[t_i, t_i + 5s]` where `t_i` is
     * the computed exponential backoff. `MAX_BACKOFF` does not apply to this value.
     */
    public suspend fun backoff(
        attempt: Int,
        errorType: RetryErrorType,
        serviceName: String? = null,
        retryAfterMillis: Long? = null,
    )
}
