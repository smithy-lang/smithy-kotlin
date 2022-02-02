/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.DelayProvider
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
 * @param options The configuration to use for this delayer.
 */
class ExponentialBackoffWithJitter(val options: ExponentialBackoffWithJitterOptions) : DelayProvider {
    private val random = Random.Default

    /**
     * Delays for an appropriate amount of time after the given attempt number.
     */
    override suspend fun backoff(attempt: Int) {
        require(attempt > 0) { "attempt was $attempt but must be greater than 0" }
        val calculatedDelayMs = options.initialDelay.inWholeMilliseconds * options.scaleFactor.pow(attempt - 1)
        val maxDelayMs = min(calculatedDelayMs, options.maxBackoff.toDouble(DurationUnit.MILLISECONDS))
        val jitterProportion = if (options.jitter > 0.0) random.nextDouble(options.jitter) else 0.0
        val delayMs = maxDelayMs * (1.0 - jitterProportion)
        delay(delayMs.toLong())
    }
}

/**
 * The configuration options for a [DelayProvider].
 * @param initialDelay The initial amount of delay
 * @param scaleFactor scale factor for determining backoff
 * @param jitter amount of jitter used in calculating delay
 * @param maxBackoff maximum amount of delay
 */
data class ExponentialBackoffWithJitterOptions(
    val initialDelay: Duration,
    val scaleFactor: Double,
    val jitter: Double,
    val maxBackoff: Duration,
) {
    init {
        require(initialDelay.isPositive()) { "initialDelayMs must be at least 0" }
        require(scaleFactor >= 1.0) { "scaleFactor must be at least 1" }
        require(jitter in 0.0..1.0) { "jitter must be between 0 and 1" }
        require(!maxBackoff.isNegative()) { "maxBackoffMs must be at least 0" }
    }

    companion object {
        /**
         * The default backoff configuration to use.
         */
        val Default = ExponentialBackoffWithJitterOptions(
            initialDelay = 10.milliseconds, // start with 10ms
            scaleFactor = 1.5, // 10ms -> 15ms -> 22.5ms -> 33.8ms -> 50.6ms -> …
            jitter = 1.0, // Full jitter,
            maxBackoff = 20_000.milliseconds, // 20s
        )
    }
}
