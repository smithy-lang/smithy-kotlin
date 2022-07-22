/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.policy

/**
 * The evaluation of a single try.
 */
public sealed class RetryDirective {
    /**
     * Indicates that retrying should stop as the result indicates a success condition.
     */
    public object TerminateAndSucceed : RetryDirective()

    /**
     * Indicates that retrying should stop as the result indicates a non-retryable failure condition.
     */
    public object TerminateAndFail : RetryDirective()

    /**
     * Indicates that retrying should continue as the result indicates a retryable failure condition.
     */
    public data class RetryError(public val reason: RetryErrorType) : RetryDirective()
}
