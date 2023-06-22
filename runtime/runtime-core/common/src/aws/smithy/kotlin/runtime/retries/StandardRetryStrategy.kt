/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.retries.delay.*
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.util.DslBuilderProperty
import aws.smithy.kotlin.runtime.util.DslFactory
import kotlinx.coroutines.CancellationException

/**
 * Implements a retry strategy utilizing backoff delayer and a token bucket for rate limiting and circuit breaking. Note
 * that the backoff delayer and token bucket work independently of each other. Either can delay retries (and the token
 * bucket can delay the initial try). The delayer is called first so that the token bucket can refill as appropriate.
 *
 * [StandardRetryStrategy] is the recommended retry mode for the majority of use cases.
 * @param config The options that control the functionality of this strategy.
 */
public open class StandardRetryStrategy(override val config: Config = Config.default()) : RetryStrategy {
    public companion object : DslFactory<Config.Builder, StandardRetryStrategy> {
        public override operator fun invoke(block: Config.Builder.() -> Unit): StandardRetryStrategy =
            StandardRetryStrategy(Config(Config.Builder().apply(block)))
    }

    /**
     * Retry the given block of code until it's successful. Note this method throws exceptions for non-successful
     * outcomes from retrying.
     */
    override suspend fun <R> retry(policy: RetryPolicy<R>, block: suspend () -> R): Outcome<R> {
        try {
            beforeInitialTry()
        } catch (ex: RetryCapacityExceededException) {
            throwCapacityExceeded<Unit>(ex, 1, null)
        }

        return doTryLoop(block, policy, 1, config.tokenBucket.acquireToken())
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
     * @return The successful [Outcome] from the final try.
     */
    private tailrec suspend fun <R> doTryLoop(
        block: suspend () -> R,
        policy: RetryPolicy<R>,
        attempt: Int,
        fromToken: RetryToken,
    ): Outcome<R> {
        val callResult = runCatching { block() }
        (callResult.exceptionOrNull() as? CancellationException)?.let { throw it }
        val evaluation = policy.evaluate(callResult)

        val nextToken = try {
            afterTry(attempt, callResult, evaluation, policy)

            when (evaluation) {
                is RetryDirective.TerminateAndSucceed ->
                    return success(fromToken, attempt, callResult)

                is RetryDirective.TerminateAndFail ->
                    throwFailure(attempt, callResult)

                is RetryDirective.RetryError ->
                    if (attempt >= config.maxAttempts) {
                        throwTooManyAttempts(attempt, callResult)
                    } else {
                        // Prep for another loop
                        config.delayProvider.backoff(attempt)
                        fromToken.scheduleRetry(evaluation.reason)
                    }
            }.also {
                beforeRetry(attempt + 1, callResult, evaluation, policy)
            }
        } catch (ex: RetryCapacityExceededException) {
            throwCapacityExceeded(ex, attempt, callResult)
        } catch (ex: Throwable) {
            fromToken.notifyFailure()
            throw ex
        }

        return doTryLoop(block, policy, attempt + 1, nextToken)
    }

    protected open suspend fun beforeInitialTry() {
        // No action necessary by default
    }

    protected open suspend fun <R> afterTry(
        attempt: Int,
        callResult: Result<R>,
        evaluation: RetryDirective,
        policy: RetryPolicy<R>,
    ) {
        // No action necessary by default
    }

    protected open suspend fun <R> beforeRetry(
        attempt: Int,
        callResult: Result<R>,
        evaluation: RetryDirective,
        policy: RetryPolicy<R>,
    ) {
        // No action necessary by default
    }

    /**
     * Handles the successful termination of the retry loop by marking the [RetryToken] as successful and getting the
     * [Result]'s value.
     * @param token The [RetryToken] used in the attempt that was successful.
     * @param result The [Result] that was evaluated to be successful.
     * @return The [Result]'s value.
     */
    private suspend fun <R> success(token: RetryToken, attempt: Int, result: Result<R>): Outcome<R> {
        token.notifySuccess()
        return when (val response = result.getOrNull()) {
            null -> Outcome.Exception(attempt, result.exceptionOrNull()!!)
            else -> Outcome.Response(attempt, response)
        }
    }

    private fun <R> throwCapacityExceeded(cause: Throwable, attempt: Int, result: Result<R>?): Nothing =
        when (val ex = result?.exceptionOrNull()) {
            null -> throw TooManyAttemptsException(
                cause.message!!,
                cause,
                attempt,
                result?.getOrNull(),
                result?.exceptionOrNull(),
            )
            else -> throw ex
        }

    /**
     * Handles the termination of the retry loop because of a non-retryable failure by throwing a
     * [RetryFailureException].
     * @param attempt The number of attempts completed.
     * @param result The [Result] that yielded the non-retryable condition.
     */
    private fun <R> throwFailure(attempt: Int, result: Result<R>): Nothing =
        when (val ex = result.exceptionOrNull()) {
            null -> throw RetryFailureException(
                "The operation resulted in a non-retryable failure",
                null,
                attempt,
                result.getOrNull(),
            )
            else -> throw ex
        }

    /**
     * Handles the termination of the retry loop because too many attempts have been made by throwing a
     * [TimedOutException].
     * @param attempt The number of attempts completed.
     * @param result The [Result] that yielded a retryable condition (but which won't be retried because we've already
     * tried too many times).
     */
    private fun <R> throwTooManyAttempts(attempt: Int, result: Result<R>): Nothing =
        when (val ex = result.exceptionOrNull()) {
            null -> throw TooManyAttemptsException(
                "Took more than ${config.maxAttempts} to get a successful response",
                null,
                attempt,
                result.getOrNull(),
                result.exceptionOrNull(),
            )
            else -> throw ex
        }

    /**
     * Configuration options for [StandardRetryStrategy]
     */
    public open class Config(builder: Builder) : RetryStrategy.Config {
        public companion object {
            /**
             * Creates a new default configuration instance. A new instance is returned for each method call so that a
             * single token bucket isn't shared across all instances. Callers that desire shared token bucket scopes
             * should pass an explicit token bucket instance.
             */
            public fun default(): Config = Config(Builder())

            /**
             * The default number of maximum attempts for new config instances
             */
            public const val DEFAULT_MAX_ATTEMPTS: Int = 3
        }

        /**
         * A delayer that can back off after the initial try to spread out the retries.
         */
        public val delayProvider: DelayProvider = builder.delayProviderProperty.supply()

        override val maxAttempts: Int = builder.maxAttempts

        /**
         * The token bucket instance. Utilizing an existing token bucket will share call capacity between scopes.
         */
        public val tokenBucket: RetryTokenBucket = builder.tokenBucketProperty.supply()

        override fun toBuilderApplicator(): RetryStrategy.Config.Builder.() -> Unit = {
            if (this is Builder) {
                delayProvider = this@Config.delayProvider
                maxAttempts = this@Config.maxAttempts
                tokenBucket = this@Config.tokenBucket
            }
        }

        /**
         * A mutable builder for a [Config]
         */
        public open class Builder : RetryStrategy.Config.Builder {
            internal val delayProviderProperty = DslBuilderProperty<DelayProvider.Config.Builder, DelayProvider>(
                ExponentialBackoffWithJitter,
                { config.toBuilderApplicator() },
            )

            /**
             * A delayer that can back off after the initial try to spread out the retries.
             */
            public var delayProvider: DelayProvider? by delayProviderProperty::instance

            /**
             * Configure a new exponential backoff delayer
             * @param block A DSL block which sets the parameters for the exponential backoff delayer
             */
            public fun delayProvider(block: ExponentialBackoffWithJitter.Config.Builder.() -> Unit) {
                delayProviderProperty.dsl(ExponentialBackoffWithJitter, block)
            }

            /**
             * Configure a new delayer
             * @param factory The delay provider factory to use for building a new instance
             * @param block A DSL block which sets the parameters for the delay provider
             */
            public fun <B : DelayProvider.Config.Builder, D : DelayProvider> delayProvider(
                factory: DslFactory<B, D>,
                block: B.() -> Unit,
            ) {
                delayProviderProperty.dsl(factory, block)
            }

            /**
             * The maximum number of attempts to make (including the first attempt)
             */
            public override var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS

            internal val tokenBucketProperty = DslBuilderProperty<RetryTokenBucket.Config.Builder, RetryTokenBucket>(
                StandardRetryTokenBucket,
                { config.toBuilderApplicator() },
            )

            /**
             * The token bucket instance. Utilizing an existing token bucket will share call capacity between scopes.
             */
            public var tokenBucket: RetryTokenBucket? by tokenBucketProperty::instance

            /**
             * Configure a new standard token bucket instance.
             * @param block A DSL block which sets the parameters for the token bucket
             */
            public fun tokenBucket(block: StandardRetryTokenBucket.Config.Builder.() -> Unit) {
                tokenBucketProperty.dsl(StandardRetryTokenBucket, block)
            }

            /**
             * Configure a new token bucket instance.
             * @param factory The token bucket factory to use for building a new instance
             * @param block A DSL block which sets the parameters for the token bucket
             */
            public fun <B : RetryTokenBucket.Config.Builder, T : RetryTokenBucket> tokenBucket(
                factory: DslFactory<B, T>,
                block: B.() -> Unit,
            ) {
                tokenBucketProperty.dsl(factory, block)
            }
        }
    }
}
