/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.retries.RetryContext
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.util.DslFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private val DYNAMODB_SERVICES = setOf("dynamodb", "dynamodb streams")

/**
 * A [RetryAwareDelayProvider] that implements the standard exponential backoff with jitter. The base delay varies by
 * error type and service name:
 *
 * - **Throttling** errors use a base delay of **1000 ms**.
 * - **DynamoDB** and **DynamoDB Streams** services use a base delay of **25 ms**.
 * - All other errors use [Config.initialDelay] (default **50 ms**).
 *
 * The delay for a given attempt is calculated as:
 * ```
 * delay = random(0, 1) * min(base * scaleFactor^(attempt - 1), maxBackoff)
 * ```
 *
 * @param config The configuration to use for this delayer.
 */
public class StandardExponentialBackoffWithJitter(
    override val config: Config = Config.Default,
) : RetryAwareDelayProvider {
    public companion object : DslFactory<Config.Builder, StandardExponentialBackoffWithJitter> {
        override fun invoke(block: Config.Builder.() -> Unit): StandardExponentialBackoffWithJitter = StandardExponentialBackoffWithJitter(Config(block))
    }

    private val random = Random.Default

    /**
     * Delays for an appropriate amount of time after the given attempt number, selecting the base delay according to
     * the [errorType] and [serviceName]. If a [RetryContext] with a non-null [RetryContext.retryAfter] is present
     * in the coroutine context, the delay is clamped to `[t_i, t_i + 5000ms]` where `t_i` is the computed exponential
     * backoff. `MAX_BACKOFF` does not apply to this value.
     */
    override suspend fun backoff(
        attempt: Int,
        errorType: RetryErrorType,
        serviceName: String?,
    ) {
        require(attempt > 0) { "attempt was $attempt but must be greater than 0" }
        val retryAfterMs = currentCoroutineContext()[RetryContext]?.retryAfter?.toDouble(DurationUnit.MILLISECONDS)
        val baseMs = when {
            errorType == RetryErrorType.Throttling -> 1000.0
            serviceName?.lowercase() in DYNAMODB_SERVICES -> 25.0
            else -> config.initialDelay.toDouble(DurationUnit.MILLISECONDS)
        }
        val exp = baseMs * config.scaleFactor.pow(attempt - 1)
        val capped = min(exp, config.maxBackoff.toDouble(DurationUnit.MILLISECONDS))
        val jitterProportion = if (config.jitter > 0.0) random.nextDouble(config.jitter) else 0.0
        val tI = capped * (1.0 - jitterProportion)

        // Clamp retry-after to [t_i, t_i + 5s]. MAX_BACKOFF does not apply.
        val delayMs = retryAfterMs?.coerceIn(tI, tI + 5000.0) ?: tI

        delay(delayMs.toLong().milliseconds)
    }

    /**
     * Configuration options for [StandardExponentialBackoffWithJitter].
     */
    public class Config(builder: Builder) : DelayProvider.Config {
        public companion object {
            /**
             * The default configuration.
             */
            public val Default: Config = Config(Builder())

            /**
             * Initializes a new config from a builder.
             * @param block A DSL builder block which sets the values for this config.
             */
            public operator fun invoke(block: Builder.() -> Unit): Config = Config(Builder().apply(block))
        }

        /**
         * The base delay for non-throttling, non-DynamoDB errors.
         */
        public val initialDelay: Duration = builder.initialDelay

        /**
         * The scale factor by which to multiply the previous max delay.
         */
        public val scaleFactor: Double = builder.scaleFactor

        /**
         * The amount of random variability over the max delay (1.0 means full jitter, 0.0 means no jitter).
         */
        public val jitter: Double = builder.jitter

        /**
         * An upper bound for the computed delay which will override the [scaleFactor].
         */
        public val maxBackoff: Duration = builder.maxBackoff

        @InternalApi
        override fun toBuilderApplicator(): DelayProvider.Config.Builder.() -> Unit = {
            if (this is Builder) {
                initialDelay = this@Config.initialDelay
                scaleFactor = this@Config.scaleFactor
                jitter = this@Config.jitter
                maxBackoff = this@Config.maxBackoff
            }
        }

        /**
         * A mutable builder for config for [StandardExponentialBackoffWithJitter].
         */
        public class Builder : ExponentialBackoffWithJitterConfig {
            /**
             * The base delay for non-throttling, non-DynamoDB errors.
             */
            override var initialDelay: Duration = 50.milliseconds

            /**
             * The scale factor by which to multiply the previous max delay.
             */
            override var scaleFactor: Double = 2.0

            /**
             * The amount of random variability over the max delay (1.0 means full jitter, 0.0 means no jitter).
             */
            override var jitter: Double = 1.0

            /**
             * An upper bound for the computed delay which will override the [scaleFactor].
             */
            override var maxBackoff: Duration = 20.seconds
        }
    }
}
