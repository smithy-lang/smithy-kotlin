/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.PlannedRemoval
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.util.DslFactory
import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * The standard implementation of a [RetryTokenBucket].
 * @param config The configuration to use for this bucket.
 * @param timeSource A monotonic time source to use for calculating the temporal token fill of the bucket.
 */
public class StandardRetryTokenBucket internal constructor(
    override val config: Config,
    private val timeSource: TimeSource,
) : RetryTokenBucket {
    /**
     * Initializes a new [StandardRetryTokenBucket].
     */
    public constructor(options: Config = Config.Default) : this(options, timeSource = TimeSource.Monotonic)

    public companion object : DslFactory<Config.Builder, StandardRetryTokenBucket> {
        override fun invoke(block: Config.Builder.() -> Unit): StandardRetryTokenBucket = StandardRetryTokenBucket(Config(Config.Builder().apply(block)))
    }

    internal var capacity = config.maxCapacity
        private set

    private var lastTimeMark = timeSource.markNow()
    private val mutex = Mutex()

    /**
     * Acquire a token from the token bucket. This method should be called before the initial retry attempt for a block
     * of code. This method may delay if there are already insufficient tokens in the bucket due to prior retry
     * failures or large numbers of simultaneous requests.
     */
    override suspend fun acquireToken(): RetryToken {
        checkoutCapacity(config.initialTryCost)
        return StandardRetryToken(config.initialTrySuccessIncrement)
    }

    private suspend fun checkoutCapacity(size: Int): Unit = mutex.withLock {
        refillCapacity()

        if (size <= capacity) {
            capacity -= size
        } else {
            if (config.useCircuitBreakerMode) {
                throw RetryCapacityExceededException("Insufficient capacity to attempt retry")
            }

            val extraRequiredCapacity = size - capacity
            val delayDuration = ceil(extraRequiredCapacity.toDouble() / config.refillUnitsPerSecond).seconds
            delay(delayDuration)
            capacity = 0
        }

        lastTimeMark = timeSource.markNow()
    }

    private fun refillCapacity() {
        val refillSeconds = lastTimeMark.elapsedNow().toDouble(DurationUnit.SECONDS)
        val refillSize = floor(config.refillUnitsPerSecond * refillSeconds).toInt()
        capacity = min(config.maxCapacity, capacity + refillSize)
    }

    private suspend fun returnCapacity(size: Int): Unit = mutex.withLock {
        refillCapacity()

        capacity = min(config.maxCapacity, capacity + size)
        lastTimeMark = timeSource.markNow()
    }

    /**
     * A standard implementation of a [RetryToken].
     * @param returnSize The amount of capacity to return to the bucket on a successful try.
     */
    internal inner class StandardRetryToken(private val returnSize: Int) : RetryToken {
        /**
         * Completes this token because retrying has been abandoned. This implementation doesn't actually increment any
         * capacity upon failure...capacity just refills based on time.
         */
        override suspend fun notifyFailure() {
            // Do nothing
        }

        /**
         * Completes this token because the previous retry attempt was successful.
         */
        override suspend fun notifySuccess() {
            returnCapacity(returnSize)
        }

        /**
         * Completes this token and requests another one because the previous retry attempt was unsuccessful.
         */
        override suspend fun scheduleRetry(reason: RetryErrorType): RetryToken {
            val size = when (reason) {
                RetryErrorType.Throttling -> config.timeoutRetryCost
                RetryErrorType.Transient -> if (config.useNewRetries) config.retryCost else config.timeoutRetryCost
                else -> config.retryCost
            }
            checkoutCapacity(size)
            return StandardRetryToken(size)
        }
    }

    /**
     * Options for configuring a [StandardRetryTokenBucket]
     */
    public class Config(builder: Builder) : RetryTokenBucket.Config {
        public companion object {
            /**
             * The default configuration to use
             */
            public val Default: Config = Config(Builder())

            /**
             * Initializes a new config instance
             * @param block A DSL builder block which sets the parameters for this config
             */
            public operator fun invoke(block: Builder.() -> Unit): Config = Config(Builder().apply(block))
        }

        /**
         * When `true`, indicates that attempts to acquire tokens or schedule retries should fail if all capacity has
         * been depleted. When `false`, calls to acquire tokens or schedule retries will delay until sufficient capacity
         * is available. This property will automatically be set to `true` if [refillUnitsPerSecond] is 0.
         */
        public val useCircuitBreakerMode: Boolean = builder.useCircuitBreakerMode

        /**
         * Whether [RetryErrorType.Transient] (timeout) retries are charged [retryCost]. When `false`, they are charged
         * [timeoutRetryCost], as in the legacy cost model.
         */
        internal val useNewRetries: Boolean = builder.useNewRetries

        /**
         * The amount of capacity to decrement for the initial try
         */
        public val initialTryCost: Int = builder.initialTryCost

        /**
         * The amount of capacity to return if the initial try is successful
         */
        public val initialTrySuccessIncrement: Int = builder.initialTrySuccessIncrement

        /**
         * The maximum capacity for the bucket
         */
        public val maxCapacity: Int = builder.maxCapacity

        /**
         * The amount of capacity to return per second. Setting this to 0 automatically sets [useCircuitBreakerMode] to
         * `true`.
         */
        public val refillUnitsPerSecond: Int = builder.refillUnitsPerSecond

        /**
         * The amount of capacity to decrement for non-throttling retries, including timeouts
         */
        public val retryCost: Int = builder.retryCost

        /**
         * The amount of capacity to decrement for throttling retries. Also used for timeout retries when
         * `useNewRetries` is `false`.
         */
        public val timeoutRetryCost: Int = builder.timeoutRetryCost

        @InternalApi
        override fun toBuilderApplicator(): RetryTokenBucket.Config.Builder.() -> Unit = {
            if (this is Builder) {
                useNewRetries = this@Config.useNewRetries
                useCircuitBreakerMode = this@Config.useCircuitBreakerMode
                initialTryCost = this@Config.initialTryCost
                initialTrySuccessIncrement = this@Config.initialTrySuccessIncrement
                maxCapacity = this@Config.maxCapacity
                refillUnitsPerSecond = this@Config.refillUnitsPerSecond
                retryCost = this@Config.retryCost
                timeoutRetryCost = this@Config.timeoutRetryCost
            }
        }

        /**
         * A mutable builder for a [Config]
         */
        public class Builder() : RetryTokenBucket.Config.Builder {
            /**
             * @param platform Ignored. Defaults do not depend on the environment.
             */
            @Deprecated(
                "The platform is ignored. This constructor will be removed in a future release.",
                ReplaceWith("Builder()"),
            )
            @PlannedRemoval(1, 9)
            public constructor(
                @Suppress("UNUSED_PARAMETER") platform: PlatformEnvironProvider = PlatformProvider.System,
            ) : this()

            /**
             * Whether [RetryErrorType.Transient] (timeout) retries are charged [retryCost]. When `false`, they are
             * charged [timeoutRetryCost], as in the legacy cost model. Defaults to `true`.
             */
            public var useNewRetries: Boolean = true

            /**
             * When `true`, indicates that attempts to acquire tokens or schedule retries should fail if all capacity
             * has been depleted. When `false`, calls to acquire tokens or schedule retries will delay until sufficient
             * capacity is available. This property will automatically be set to `true` if [refillUnitsPerSecond] is 0.
             */
            public var useCircuitBreakerMode: Boolean = true

            /**
             * The amount of capacity to decrement for the initial try
             */
            public var initialTryCost: Int = 0

            /**
             * The amount of capacity to return if the initial try is successful
             */
            public var initialTrySuccessIncrement: Int = 1

            /**
             * The maximum capacity for the bucket
             */
            public var maxCapacity: Int = 500

            /**
             * The amount of capacity to return per second. Setting this to 0 automatically sets [useCircuitBreakerMode]
             * to `true`.
             */
            public var refillUnitsPerSecond: Int = 0
                set(value) {
                    if (value == 0) useCircuitBreakerMode = true
                    field = value
                }

            /**
             * The amount of capacity to decrement for non-throttling retries, including timeouts
             */
            public var retryCost: Int = 14

            /**
             * The amount of capacity to decrement for throttling retries. Also used for timeout retries when
             * [useNewRetries] is `false`.
             */
            public var timeoutRetryCost: Int = 5
        }
    }
}
