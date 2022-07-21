/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.policy

/**
 * A [RetryPolicy] that iterates through a list of [Acceptor] instances to determine the appropriate [RetryDirective].
 * Each [Acceptor] is evaluated in list order until one returns a non-null result. If no acceptor returns a non-null
 * result, the directive is [RetryDirective.RetryError] on success or [RetryDirective.TerminateAndFail] on exception.
 * @param I The type of the input to the operation.
 * @param O The type of the output from the operation.
 * @param input The input to the operation.
 * @param acceptors A list of [Acceptor] instances to be tried in order.
 */
public class AcceptorRetryPolicy<in I, in O>(
    private val input: I,
    private val acceptors: List<Acceptor<I, O>>,
) : RetryPolicy<O> {
    override fun evaluate(result: Result<O>): RetryDirective =
        acceptors.firstNotNullOfOrNull { it.evaluate(input, result) } ?: result.toDefaultDirective()

    private fun Result<O>.toDefaultDirective(): RetryDirective =
        if (isSuccess) RetryDirective.RetryError(RetryErrorType.ServerSide) else RetryDirective.TerminateAndFail
}
