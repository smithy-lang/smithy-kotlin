/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.policy

/**
 * A policy that evaluates a [Result] from a retry attempt and indicates the action a
 * [aws.smithy.kotlin.runtime.retries.RetryStrategy] should take next.
 */
public interface RetryPolicy<in R> {
    /**
     * Evaluate the given retry attempt.
     * @param result The [Result] of the retry attempt.
     * @return A [RetryDirective] indicating what action the [aws.smithy.kotlin.runtime.retries.RetryStrategy] should
     * take next.
     */
    public fun evaluate(result: Result<R>): RetryDirective
}
