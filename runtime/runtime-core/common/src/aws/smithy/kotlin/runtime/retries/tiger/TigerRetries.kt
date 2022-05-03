/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.retries.tiger

import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration

enum class RetryErrorType {
    Transient,
    Throttling,
    ServerError,
    ClientError,
}

interface DelayProvider {
    fun nextDelay(): Duration
}

interface RetryToken

interface RetryStrategy {
    suspend fun acquireRetryToken(partitionId: String, timeout: Duration): RetryToken
    suspend fun waitForRetry(token: RetryToken, errorType: RetryErrorType)
    suspend fun recordSuccess(token: RetryToken)
}

/* Implementation */

private const val MS_PER_S = 1000

class PrivateRetryToken(val partitionId: String, val returnSize: Int) : RetryToken

class TokenBucketRetryStrategy(val options: TokenBucketRetryStrategyOptions, private val clock: Clock) : RetryStrategy {
    private val partitions = mutableMapOf("" to TokenBucket())
    private val partitionsMutex = Mutex()

    override suspend fun acquireRetryToken(partitionId: String, timeout: Duration): RetryToken {
        val bucket = getBucket(partitionId)
        bucket.checkoutCapacity(options.initialTryCost, timeout)
        return PrivateRetryToken(partitionId, options.returnSize)
    }

    private suspend inline fun getBucket(partitionId: String): TokenBucket =
        // Optimistic check
        partitions[partitionId] ?: partitionsMutex.withLock {
            // Double-checked check under lock
            partitions[partitionId] ?: TokenBucket().also {
                // Save the new partition
                partitions[partitionId] = it
            }
        }

    override suspend fun waitForRetry(token: RetryToken, errorType: RetryErrorType) {
        delay(options.delayProvider.nextDelay())

        token as PrivateRetryToken
        val cost = options.errorTryCosts[errorType] ?: options.initialTryCost
        val bucket = getBucket(token.partitionId)
        bucket.checkoutCapacity(cost, null)
    }

    override suspend fun recordSuccess(token: RetryToken) {
        token as PrivateRetryToken
        val bucket = getBucket(token.partitionId)
        bucket.returnCapacity(token.returnSize)
    }

    private inner class TokenBucket {
        private var capacity = options.maxCapacity
        private val capacityMutex = Mutex()
        private var lastTimestamp = now()

        suspend fun checkoutCapacity(amount: Int, timeout: Duration?) =
            capacityMutex.withLock {
                refillCapacity()

                if (amount <= capacity) {
                    capacity -= amount
                } else {
                    if (options.circuitBreakerMode) throw Exception("No capacity to attempt retry")

                    val extraReqCapacity = amount - capacity
                    val delayMs = ceil(extraReqCapacity.toDouble() / options.refillUnitsPerSecond * MS_PER_S).toLong()

                    if (timeout != null && delayMs >= timeout.inWholeMilliseconds) {
                        throw Exception("No capacity before timeout")
                    }

                    delay(delayMs)
                    capacity = 0
                }

                lastTimestamp = now()
            }

        private fun now(): Long = clock.now().epochMilliseconds

        private fun refillCapacity() {
            val refillMs = now() - lastTimestamp
            val refillSize = floor(options.refillUnitsPerSecond.toDouble() / MS_PER_S * refillMs).toInt()
            capacity = min(options.maxCapacity, capacity + refillSize)
        }

        suspend fun returnCapacity(size: Int) = capacityMutex.withLock {
            refillCapacity()

            capacity = min(options.maxCapacity, capacity + size)
            lastTimestamp = now()
        }
    }
}

data class TokenBucketRetryStrategyOptions(
    val delayProvider: DelayProvider,
    val maxCapacity: Int,
    val initialTryCost: Int,
    val errorTryCosts: Map<RetryErrorType, Int>,
    val returnSize: Int,
    val refillUnitsPerSecond: Int,
    val circuitBreakerMode: Boolean,
)

class ExponentialBackoffDelayProvider(private val options: ExponentialBackoffDelayProviderOptions) : DelayProvider {
    var index = 0

    override fun nextDelay(): Duration {
        val delay = if (index == 0) {
            Duration.ZERO
        } else {
            val maxDelay = options.initialDelay * options.backoffFactor.pow(index - 1)
            val jitter = 1.0 - (Random.nextDouble() * options.maxJitter)
            maxDelay * jitter
        }
        index++
        return delay
    }
}

data class ExponentialBackoffDelayProviderOptions(
    val initialDelay: Duration,
    val backoffFactor: Double,
    val maxJitter: Double,
)
