/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.policy

/**
 * The evaluation of a single try.
 */
sealed class RetryDirective {
    /**
     * Indicates that retrying should stop as the result indicates a success condition.
     */
    object TerminateAndSucceed : RetryDirective()

    /**
     * Indicates that retrying should stop as the result indicates a non-retryable failure condition.
     */
    object TerminateAndFail : RetryDirective()

    /**
     * Indicates that retrying should continue as the result indicates a retryable failure condition.
     */
    data class RetryError(val reason: RetryErrorType) : RetryDirective()
}
