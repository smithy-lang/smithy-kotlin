/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.util.DslFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * A client-side rate limiter backed by a token bucket. This limiter adaptively updates the refill rate of the bucket
 * based on the number of successful transactions vs throttling errors. This limiter applies a smoothing function in an
 * attempt to converge on the ideal transaction rate feasible for downstream systems.
 */
@OptIn(ExperimentalTime::class)
public class AdaptiveClientRateLimiter internal constructor(
    public override val config: Config = Config.Default,
    private val timeSource: TimeSource,
    private val rateMeasurer: AdaptiveRateMeasurer,
    private val rateCalculator: CubicRateCalculator,
) : ClientRateLimiter {
    /**
     * Initializes a new [AdaptiveClientRateLimiter]
     * @param config The configuration parameters for this limiter
     */
    public constructor(config: Config = Config.Default) : this(
        config,
        TimeSource.Monotonic,
        AdaptiveRateMeasurer(config, TimeSource.Monotonic),
        CubicRateCalculator(config, TimeSource.Monotonic),
    )

    public companion object : DslFactory<Config.Builder, AdaptiveClientRateLimiter> {
        /**
         * Initializes a new [AdaptiveClientRateLimiter]
         * @param block A DSL block which sets the configuration parameters for this limiter
         */
        public override operator fun invoke(block: Config.Builder.() -> Unit): AdaptiveClientRateLimiter =
            AdaptiveClientRateLimiter(Config(Config.Builder().apply(block)))
    }

    private var capacity = 0.0
    private var lastTimeMark: TimeMark? = null
    internal var refillUnitsPerSecond = 0.0
    private var maxCapacity = 0.0
    private val mutex = Mutex()

    override suspend fun acquire(cost: Int): Unit = mutex.withLock {
        if (rateCalculator.throttlingEnabled) {
            refillCapacity()
            if (cost <= capacity) {
                capacity -= cost
            } else {
                val extraRequiredCapacity = cost - capacity
                val delayDuration = (extraRequiredCapacity / refillUnitsPerSecond).seconds
                delay(delayDuration)
                capacity = 0.0
            }
        }
    }

    private fun refillCapacity() {
        lastTimeMark?.let {
            val refillSeconds = it.elapsedNow().toDouble(DurationUnit.SECONDS)
            val refillCapacity = refillUnitsPerSecond * refillSeconds
            capacity = min(maxCapacity, capacity + refillCapacity)
        }
        lastTimeMark = timeSource.markNow()
    }

    override suspend fun update(errorType: RetryErrorType?): Unit = mutex.withLock {
        val measuredTxRate = rateMeasurer.updateMeasuredRate()
        val calculatedRate = rateCalculator.calculate(errorType, measuredTxRate, refillUnitsPerSecond)
        val newRate = min(calculatedRate, 2 * measuredTxRate)
        updateRefillRate(newRate)
    }

    private fun updateRefillRate(newRate: Double) {
        refillCapacity()
        refillUnitsPerSecond = max(newRate, config.minFillRate)
        maxCapacity = max(newRate, config.minCapacity)
        capacity = min(capacity, maxCapacity)
    }

    /**
     * The configuration for an adaptive client-side rate limiter
     */
    public class Config(builder: Builder) : ClientRateLimiter.Config {
        public companion object {
            /**
             * The default configuration
             */
            public val Default: Config = Config(Builder())
        }

        /**
         * How much to scale back after receiving a throttling response. Ranges from 0.0 (do not scale back) to 1.0
         * (scale back completely). Defaults to 0.7 (scale back 70%).
         *
         * **Note**: This is an advanced parameter and modifying it is not recommended.
         */
        public val beta: Double = builder.beta

        /**
         * The duration of individual measurement buckets used in measuring the effective transaction rate. Defaults to
         * 0.5 seconds.
         */
        public val measurementBucketDuration: Duration = builder.measurementBucketDuration

        /**
         * The minimum capacity of permits. Defaults to 1.
         */
        public val minCapacity: Double = builder.minCapacity

        /**
         * The minimum refill rate (per second) of permits. Defaults to 0.5.
         */
        public val minFillRate: Double = builder.minFillRate

        /**
         * How much to scale up after receiving a successful response. Ranges from 0.0 (do not scale up) to 1.0 (scale
         * up completely). Defaults to 0.4 (scale up 40%).
         *
         * **Note**: This is an advanced parameter and modifying it is not recommended.
         */
        public val scaleConstant: Double = builder.scaleConstant

        /**
         * The exponential smoothing factor to apply when measuring the effective rate of transactions. Ranges from 0.0
         * (do not accept new rate updates) to 1.0 (immediately accept new rates with no smoothing). Defaults to 0.8
         * (new rates are factored into the measured rate at 80%).
         *
         * **Note**: This is an advanced parameter and modifying it is not recommended.
         */
        public val smoothing: Double = builder.smoothing

        override fun toBuilderApplicator(): ClientRateLimiter.Config.Builder.() -> Unit = {
            if (this is Builder) {
                beta = this@Config.beta
                measurementBucketDuration = this@Config.measurementBucketDuration
                minCapacity = this@Config.minCapacity
                minFillRate = this@Config.minFillRate
                scaleConstant = this@Config.scaleConstant
                smoothing = this@Config.smoothing
            }
        }

        public class Builder : ClientRateLimiter.Config.Builder {
            /**
             * How much to scale back after receiving a throttling response. Ranges from 0.0 (do not scale back) to 1.0
             * (scale back completely). Defaults to 0.7 (scale back 70%).
             *
             * **Note**: This is an advanced parameter and modifying it is not recommended.
             */
            public var beta: Double = 0.7

            /**
             * The duration of individual measurement buckets used in measuring the effective transaction rate. Defaults
             * to 0.5 seconds.
             */
            public var measurementBucketDuration: Duration = 0.5.seconds

            /**
             * The minimum capacity of permits. Defaults to 1.
             */
            public var minCapacity: Double = 1.0

            /**
             * The minimum refill rate (per second) of permits. Defaults to 0.5.
             */
            public var minFillRate: Double = 0.5

            /**
             * How much to scale up after receiving a successful response. Ranges from 0.0 (do not scale up) to 1.0
             * (scale up completely). Defaults to 0.4 (scale up 40%).
             *
             * **Note**: This is an advanced parameter and modifying it is not recommended.
             */
            public var scaleConstant: Double = 0.4

            /**
             * The exponential smoothing factor to apply when measuring the effective rate of transactions. Ranges from
             * 0.0 (do not accept new rate updates) to 1.0 (immediately accept new rates with no smoothing). Defaults to
             * 0.8 (new rates are factored into the measured rate at 80%).
             *
             * **Note**: This is an advanced parameter and modifying it is not recommended.
             */
            public var smoothing: Double = 0.8
        }
    }
}

@OptIn(ExperimentalTime::class)
internal class CubicRateCalculator(
    private val config: AdaptiveClientRateLimiter.Config,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    internal var lastMaxRate: Double = 0.0,
    internal var lastThrottleTime: TimeMark = timeSource.markNow(),
) {
    var throttlingEnabled: Boolean = false
        private set

    internal var timeWindow: Double = calculateTimeWindow()

    fun calculate(
        errorType: RetryErrorType?,
        measuredTxRate: Double,
        refillUnitsPerSecond: Double,
    ): Double {
        val calculatedRate = if (errorType == RetryErrorType.Throttling) {
            lastMaxRate = if (throttlingEnabled) min(measuredTxRate, refillUnitsPerSecond) else measuredTxRate
            timeWindow = calculateTimeWindow()
            lastThrottleTime = timeSource.markNow()
            throttlingEnabled = true
            cubicThrottle(lastMaxRate)
        } else {
            cubicSuccess()
        }

        return calculatedRate
    }

    internal fun calculateTimeWindow() = ((lastMaxRate * (1 - config.beta)) / config.scaleConstant).pow(1.0 / 3)

    internal fun cubicSuccess(): Double {
        val deltaSeconds = lastThrottleTime.elapsedNow().toDouble(DurationUnit.SECONDS)
        return (config.scaleConstant * (deltaSeconds - timeWindow).pow(3) + lastMaxRate)
    }

    internal fun cubicThrottle(rate: Double) = rate * config.beta
}

@OptIn(ExperimentalTime::class)
internal class AdaptiveRateMeasurer(
    private val config: AdaptiveClientRateLimiter.Config,
    private val timeSource: TimeSource,
    private var lastTxBucketMark: TimeMark = timeSource.markNow(),
    internal var measuredTxRate: Double = 0.0,
    private var requestCount: Int = 0,
) {
    private val bucketsPerSecond = 1 / config.measurementBucketDuration.toDouble(DurationUnit.SECONDS)

    fun updateMeasuredRate(): Double {
        requestCount++

        val delta = lastTxBucketMark.elapsedNow()

        val bucketDelta = floor(delta / config.measurementBucketDuration)

        if (bucketDelta >= 1.0) {
            val currentRate = requestCount / bucketDelta * bucketsPerSecond
            measuredTxRate = (currentRate * config.smoothing) + (measuredTxRate * (1 - config.smoothing))

            lastTxBucketMark = lastTxBucketMark + bucketDelta * config.measurementBucketDuration
            requestCount = 0
        }

        return measuredTxRate
    }
}
