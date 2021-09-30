/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries

/**
 * A policy that evaluates a [Result] from a retry attempt and indicates action a [RetryStrategy] should take next.
 */
interface RetryPolicy<in R> {
    /**
     * Evaluate the given retry attempt.
     * @param result The [Result] of the retry attempt.
     * @return A [RetryDirective] indicating what action the [RetryStrategy] should take next.
     */
    fun evaluate(result: Result<R>): RetryDirective
}
