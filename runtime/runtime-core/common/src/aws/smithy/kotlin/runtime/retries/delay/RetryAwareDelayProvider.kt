/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType

/**
 * A [DelayProvider] that can adjust backoff based on retry context such as the error type and service name.
 */
public interface RetryAwareDelayProvider : DelayProvider {
    /**
     * Delays for an appropriate amount of time after the given attempt number, taking into account the type of error
     * and optionally the service name.
     * @param attempt The ordinal index of the attempt.
     * @param errorType The type of error that triggered the retry.
     * @param serviceName The optional service name, used for service-specific backoff (e.g., DynamoDB).
     */
    public suspend fun backoff(attempt: Int, errorType: RetryErrorType, serviceName: String? = null)
}
