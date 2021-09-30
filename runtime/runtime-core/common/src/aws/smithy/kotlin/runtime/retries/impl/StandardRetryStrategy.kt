/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Implements a retry strategy utilizing backoff delayer and a token bucket for rate limiting and circuit breaking. Note
 * that the backoff delayer and token bucket work independently of each other. Either can delay retries (and the token
 * bucket can delay the initial try). The delayer is called first so that the token bucket can refill as appropriate.
 * @param options The options that control the functionality of this strategy.
 * @param tokenBucket The token bucket instance. Utilizing an existing token bucket will share call capacity between
 * those scopes.
 * @param backoffDelayer A delayer that can back off after the initial try to spread out the retries.
 */
class StandardRetryStrategy(
    val options: StandardRetryStrategyOptions,
    private val tokenBucket: RetryTokenBucket,
    private val backoffDelayer: BackoffDelayer,
) : RetryStrategy {
    /**
     * Retry the given block of code until it's successful. Note this method throws exceptions for non-successful
     * outcomes from retrying.
     */
    override suspend fun <R> retry(policy: RetryPolicy<R>, block: suspend () -> R): R =
        withTimeout(options.maxTimeMs) {
            doTryLoop(block, policy, 1, tokenBucket.acquireToken(), null)
        }

    /**
     * Perform a single iteration of the try loop. Execute the block of code, evaluate the result, and take action to
     * terminate or enter the next iteration of the try loop.
     * @param block The block of code to retry.
     * @param policy The [RetryPolicy] to use for evaluating the execution result.
     * @param attempt The ordinal index of the retry loop (starting at 1).
     * @param fromToken A [RetryToken] which grants the strategy capacity to execute a try. This token is resolved
     * inside the function by calling [notifySuccess][RetryToken.notifySuccess],
     * [notifyFailure][RetryToken.notifyFailure], or [scheduleRetry][RetryToken.scheduleRetry].
     * @param previousResult The [Result] from the prior loop iteration. This is used in the case of a timeout to
     * include in the thrown exception.
     */
    private tailrec suspend fun <R> doTryLoop(
        block: suspend () -> R,
        policy: RetryPolicy<R>,
        attempt: Int,
        fromToken: RetryToken,
        previousResult: Result<R>?,
    ): R {
        val callResult = runCatching { block() }
        if (callResult.exceptionOrNull() is TimeoutCancellationException) {
            throwTimeOut(fromToken, attempt, previousResult)
        }

        val nextToken = try {
            when (val evaluation = policy.evaluate(callResult)) {
                is RetryDirective.TerminateAndSucceed ->
                    return success(fromToken, callResult)

                is RetryDirective.TerminateAndFail ->
                    throwFailure(fromToken, attempt, callResult)

                is RetryDirective.RetryError ->
                    if (attempt >= options.maxAttempts) {
                        throwTooManyAttempts(fromToken, attempt, callResult)
                    } else {
                        // Prep for another loop
                        backoffDelayer.backoff(attempt)
                        fromToken.scheduleRetry(evaluation.reason)
                    }
            }
        } catch (e: TimeoutCancellationException) {
            throwTimeOut(fromToken, attempt, callResult)
        }

        return doTryLoop(block, policy, attempt + 1, nextToken, callResult)
    }

    /**
     * Handles the successful termination of the retry loop by marking the [RetryToken] as successful and getting the
     * [Result]'s value.
     * @param token The [RetryToken] used in the attempt that was successful.
     * @param result The [Result] that was evaluated to be successful.
     * @return The [Result]'s value.
     */
    private suspend fun <R> success(token: RetryToken, result: Result<R>): R {
        token.notifySuccess()
        return result.getOrNull()!!
    }

    /**
     * Handles the termination of the retry loop because of a non-retryable failure by marking the [RetryToken] as
     * failed and throwing a [RetryFailureException].
     * @param token The [RetryToken] used in the attempt that was unsuccessful.
     * @param attempt The number of attempts completed.
     * @param result The [Result] that yielded the non-retryable condition.
     */
    private suspend fun <R> throwFailure(token: RetryToken, attempt: Int, result: Result<R>): Nothing {
        token.notifyFailure()
        throw RetryFailureException(
            "The operation failed",
            result.exceptionOrNull(),
            attempt,
            result.getOrNull(),
        )
    }

    /**
     * Handles the termination of the retry loop because too much time has elapsed by marking the [RetryToken] as failed
     * and throwing a [TimedOutException].
     * @param token The [RetryToken] used in the attempt that was waiting or executing when the timeout occurred.
     * @param attempt The number of attempts completed.
     * @param previousResult The last result that was received (i.e., from the prior loop iteration).
     */
    private suspend fun <R> throwTimeOut(token: RetryToken, attempt: Int, previousResult: Result<R>?): Nothing {
        token.notifyFailure()
        throw TimedOutException(
            "Took more than ${options.maxTimeMs}ms to yield a result",
            attempt,
            previousResult?.getOrNull(),
            previousResult?.exceptionOrNull(),
        )
    }

    /**
     * Handles the termination of the retry loop because too many attempts have been made by marking the [RetryToken] as
     * failed and throwing a [TimedOutException].
     * @param token The [RetryToken] used in the attempt that was unsuccessful.
     * @param attempt The number of attempts completed.
     * @param result The [Result] that yielded a retryable condition (but which won't be retried because we've already
     * tried too many times).
     */
    private suspend fun <R> throwTooManyAttempts(token: RetryToken, attempt: Int, result: Result<R>): Nothing {
        token.notifyFailure()
        throw TooManyAttemptsException(
            "Took more than ${options.maxAttempts} to get a successful response",
            attempt,
            result.getOrNull(),
            result.exceptionOrNull(),
        )
    }
}

/**
 * Defines configuration for a [StandardRetryStrategy].
 * @param maxTimeMs The maximum amount of time to retry (in milliseconds).
 * @param maxAttempts The maximum number of attempts to make (including the first attempt).
 */
data class StandardRetryStrategyOptions(val maxTimeMs: Long, val maxAttempts: Int) {
    companion object {
        /**
         * The default retry strategy configuration.
         */
        val Default = StandardRetryStrategyOptions(maxTimeMs = 10_000, maxAttempts = 10)
    }
}
