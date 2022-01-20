/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries

/**
 * Represents the outcome of a repeated operation. This type is similar to a [Result] except it is a union type and has
 * no flag for "success" since exceptional outcomes do not necessarily represent "failure".
 * @param T The type of non-exception result (if present).
 */
sealed class Outcome<out T> {
    /**
     * The number of attempts executed in order to reach the outcome. Starts at 1.
     */
    abstract val attempts: Int

    /**
     * An outcome that includes a normal (i.e., non-exceptional) response.
     * @param T The type of result.
     * @param attempts The number of attempts executed in order to reach the outcome. Starts at 1.
     * @param response The response to an operation.
     */
    data class ResponseOutcome<out T>(override val attempts: Int, val response: T) : Outcome<T>()

    /**
     * An outcome that includes an exception.
     * @param attempts The number of attempts executed in order to reach the outcome. Starts at 1.
     * @param exception The exception resulting from the operation.
     */
    data class ExceptionOutcome(override val attempts: Int, val exception: Throwable) : Outcome<Nothing>()
}

/**
 * Gets the non-exceptional response in this outcome if it exists. Otherwise, throws the exception in this outcome.
 */
fun <T> Outcome<T>.getOrThrow(): T = when (this) {
    is Outcome.ResponseOutcome -> response
    is Outcome.ExceptionOutcome -> throw exception
}
