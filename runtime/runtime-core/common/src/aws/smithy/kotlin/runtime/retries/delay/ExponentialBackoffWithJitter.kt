/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.CoreSettings
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.retries.RetryContext
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.util.DslFactory
import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * A [DelayProvider] that implements exponential backoff with jitter
 * (i.e., randomization of delay amount).
 * When [RetryContext.errorType] is non-null, the delay provider uses it to select the base delay.
 * When [RetryContext.retryAfter] is non-null, it is used to clamp the computed delay.
 * When either field is null, the provider falls back to
 * [Config.initialDelay] as the base and pure exponential backoff respectively.
 *
 * The delay for a given attempt is calculated as:
 * ```
 * delay = random(1 - jitter, 1) * min(base * scaleFactor^(attempt - 1), maxBackoff)
 * ```
 *
 * @param config The configuration to use for this delayer.
 */
public class ExponentialBackoffWithJitter(
    override val config: Config = Config.Default,
) : DelayProvider {
    public companion object : DslFactory<Config.Builder, ExponentialBackoffWithJitter> {
        override fun invoke(block: Config.Builder.() -> Unit): ExponentialBackoffWithJitter = ExponentialBackoffWithJitter(Config(block))
    }

    private val random = Random.Default

    /**
     * Delays for an appropriate amount of time after the given attempt number.
     */
    override suspend fun backoff(attempt: Int) {
        require(attempt > 0) { "attempt was $attempt but must be greater than 0" }

        val retryCtx = currentCoroutineContext()[RetryContext]
        val retryAfterMs = retryCtx?.retryAfter?.toDouble(DurationUnit.MILLISECONDS)
        val errorType = retryCtx?.errorType

        val baseMs = when (errorType) {
            RetryErrorType.Throttling -> config.throttlingBaseDelay.toDouble(DurationUnit.MILLISECONDS)
            else -> config.initialDelay.toDouble(DurationUnit.MILLISECONDS)
        }

        val exp = baseMs * config.scaleFactor.pow(attempt - 1)
        val capped = min(exp, config.maxBackoff.toDouble(DurationUnit.MILLISECONDS))
        val jitterProportion = if (config.jitter > 0.0) random.nextDouble(config.jitter) else 0.0
        val tI = capped * (1.0 - jitterProportion)

        val overshootMs = config.retryAfterMaxOvershoot.toDouble(DurationUnit.MILLISECONDS)
        val delayMs = retryAfterMs?.coerceIn(tI, tI + overshootMs) ?: tI

        delay(delayMs.toLong().milliseconds)
    }

    /**
     * Configuration options for [ExponentialBackoffWithJitter]
     */
    public class Config(builder: Builder) : DelayProvider.Config {
        public companion object {
            /**
             * The default configuration
             */
            public val Default: Config = Config(Builder())

            /**
             * Initializes a new config from a builder
             * @param block A DSL builder block which sets the values for this config
             */
            public operator fun invoke(block: Builder.() -> Unit): Config = Config(Builder().apply(block))
        }

        /**
         * The base delay for non-throttling errors
         */
        public val initialDelay: Duration = builder.initialDelay

        /**
         * The base delay used for throttling errors
         */
        public val throttlingBaseDelay: Duration = builder.throttlingBaseDelay

        /**
         * The scale factor by which to multiply the previous max delay
         */
        public val scaleFactor: Double = builder.scaleFactor

        /**
         * The amount of random variability over the max delay (1.0 means full jitter, 0.0 means no jitter)
         */
        public val jitter: Double = builder.jitter

        /**
         * An upper bound for the computed delay which will override the [scaleFactor]
         */
        public val maxBackoff: Duration = builder.maxBackoff

        /**
         * The maximum amount the retry-after value can exceed the computed backoff
         */
        public val retryAfterMaxOvershoot: Duration = builder.retryAfterMaxOvershoot

        @InternalApi
        override fun toBuilderApplicator(): DelayProvider.Config.Builder.() -> Unit = {
            if (this is Builder) {
                initialDelay = this@Config.initialDelay
                scaleFactor = this@Config.scaleFactor
                jitter = this@Config.jitter
                maxBackoff = this@Config.maxBackoff
                throttlingBaseDelay = this@Config.throttlingBaseDelay
                retryAfterMaxOvershoot = this@Config.retryAfterMaxOvershoot
            }
        }

        /**
         * A mutable builder for config for [ExponentialBackoffWithJitter]
         */
        public class Builder(platform: PlatformEnvironProvider = PlatformProvider.System) : DelayProvider.Config.Builder {
            private val useNewRetries = CoreSettings.resolveNewRetriesEnabled(platform)

            /**
             * The base delay for non-throttling errors
             */
            public var initialDelay: Duration = if (useNewRetries) 50.milliseconds else 10.milliseconds

            /**
             * The scale factor by which to multiply the previous max delay
             * */
            public var scaleFactor: Double = if (useNewRetries) 2.0 else 1.5

            /**
             * The amount of random variability over the max delay (1.0 means full jitter, 0.0 means no jitter)
             * */
            public var jitter: Double = 1.0

            /**
             * An upper bound for the computed delay which will override the [scaleFactor]
             * */
            public var maxBackoff: Duration = 20.seconds

            /**
             * The base delay used for throttling errors
             * */
            public var throttlingBaseDelay: Duration = 1.seconds

            /**
             *  The maximum amount the retry-after value can exceed the computed backoff
             *  */
            public var retryAfterMaxOvershoot: Duration = 5.seconds
        }
    }
}
