/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.DslFactory
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * A [DelayProvider] that implements exponentially increasing delays and jitter (i.e., randomization of delay amount).
 * This delayer calculates a maximum delay time from the initial delay amount, the scale factor, and the attempt number.
 * It then randomly reduces that time down to something less based on the jitter configuration.
 *
 * For instance, a jitter
 * configuration of 0.5 means that up to 50% of the max delay time could be reduced. A jitter configuration of 1.0 means
 * that 100% of the max delay time could be reduced (potentially down to 0). A jitter configuration of 0.0 means jitter
 * is disabled.
 *
 * @param config The configuration to use for this delayer.
 */
public class ExponentialBackoffWithJitter(override val config: Config = Config.Default) : DelayProvider {
    public companion object : DslFactory<Config.Builder, ExponentialBackoffWithJitter> {
        override fun invoke(block: Config.Builder.() -> Unit): ExponentialBackoffWithJitter =
            ExponentialBackoffWithJitter(Config(block))
    }

    private val random = Random.Default

    /**
     * Delays for an appropriate amount of time after the given attempt number.
     */
    override suspend fun backoff(attempt: Int) {
        require(attempt > 0) { "attempt was $attempt but must be greater than 0" }
        val calculatedDelayMs = config.initialDelay.inWholeMilliseconds * config.scaleFactor.pow(attempt - 1)
        val maxDelayMs = min(calculatedDelayMs, config.maxBackoff.toDouble(DurationUnit.MILLISECONDS))
        val jitterProportion = if (config.jitter > 0.0) random.nextDouble(config.jitter) else 0.0
        val delayMs = maxDelayMs * (1.0 - jitterProportion)
        delay(delayMs.toLong())
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
         * The initial maximum amount of delay
         */
        public val initialDelay: Duration = builder.initialDelay

        /**
         * The scale factor by which to multiply the previous max delay
         */
        public val scaleFactor: Double = builder.scaleFactor

        /**
         * The amount of random variability over the max delay (1.0 mean full jitter, 0.0 means no jitter)
         */
        public val jitter: Double = builder.jitter

        /**
         * An upper bound for max delay which will override the [scaleFactor]
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
         * A mutable builder for config for [ExponentialBackoffWithJitter]
         */
        public class Builder : DelayProvider.Config.Builder {
            /**
             * The initial maximum amount of delay
             */
            public var initialDelay: Duration = 10.milliseconds

            /**
             * The scale factor by which to multiply the previous max delay
             */
            public var scaleFactor: Double = 1.5

            /**
             * The amount of random variability over the max delay (1.0 mean full jitter, 0.0 means no jitter)
             */
            public var jitter: Double = 1.0

            /**
             * An upper bound for max delay which will override the [scaleFactor]
             */
            public var maxBackoff: Duration = 20.seconds
        }
    }
}
