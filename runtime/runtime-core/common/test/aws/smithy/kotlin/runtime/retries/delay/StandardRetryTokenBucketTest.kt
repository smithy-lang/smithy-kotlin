/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

private const val DEFAULT_RETRY_COST = 2
private const val DEFAULT_TIMEOUT_RETRY_COST = 3

@OptIn(ExperimentalTime::class)
class StandardRetryTokenBucketTest {
    @Test
    fun testWaitForCapacity() = runTest {
        // A bucket that only allows one initial try per second
        val bucket = tokenBucket(initialTryCost = 10)

        assertEquals(10, bucket.capacity)
        assertTime(0.seconds) { bucket.acquireToken() }
        assertEquals(0, bucket.capacity)
        assertTime(1.seconds) { bucket.acquireToken() }
    }

    @Test
    fun testReturnCapacityOnSuccess() = runTest {
        // A bucket that costs capacity for an initial try and doesn't return the same capacity (for easy measuring)
        val bucket = tokenBucket(initialTryCost = 5, initialTrySuccessIncrement = 3)

        assertEquals(10, bucket.capacity)
        val initialToken = assertTime(0.seconds) { bucket.acquireToken() }
        assertEquals(5, bucket.capacity)
        assertTime(0.seconds) { initialToken.notifySuccess() }
        assertEquals(8, bucket.capacity)
    }

    @Test
    fun testNoCapacityChangeOnFailure() = runTest {
        // A bucket that costs capacity for an initial try
        val bucket = tokenBucket(initialTryCost = 1)

        assertEquals(10, bucket.capacity)
        val initialToken = assertTime(0.seconds) { bucket.acquireToken() }
        assertEquals(9, bucket.capacity)
        assertTime(0.seconds) { initialToken.notifyFailure() }
        assertEquals(9, bucket.capacity)
    }

    @Test
    fun testRetryCapacityAdjustments() = runTest {
        mapOf(
            RetryErrorType.Throttling to DEFAULT_TIMEOUT_RETRY_COST,
            RetryErrorType.Transient to DEFAULT_TIMEOUT_RETRY_COST,
            RetryErrorType.ClientSide to DEFAULT_RETRY_COST,
            RetryErrorType.ServerSide to DEFAULT_RETRY_COST,
        ).forEach { (errorType, cost) ->
            val bucket = tokenBucket()

            assertEquals(10, bucket.capacity)
            val initialToken = assertTime(0.seconds) { bucket.acquireToken() }
            assertEquals(10, bucket.capacity)
            assertTime(0.seconds) { initialToken.scheduleRetry(errorType) }
            assertEquals(10 - cost, bucket.capacity)
        }
    }

    @Test
    fun testRefillOverTime() = runTest {
        val timeSource = TestTimeSource()

        // A bucket that costs capacity for an initial try
        val bucket = tokenBucket(initialTryCost = 5, timeSource = timeSource)

        assertEquals(10, bucket.capacity)
        assertTime(0.seconds) { bucket.acquireToken() }
        assertEquals(5, bucket.capacity)

        // Refill rate is 10/s == 1/100ms so after 250ms we should have 2 more tokens.
        timeSource += 250.milliseconds

        assertTime(0.seconds) { bucket.acquireToken() }
        assertEquals(2, bucket.capacity) // We had 5, 2 refilled, and then we decremented 5 more.
    }

    @Test
    fun testCircuitBreakerMode() = runTest {
        // A bucket that only allows one initial try per second
        val bucket = tokenBucket(initialTryCost = 10, useCircuitBreakerMode = true)

        assertEquals(10, bucket.capacity)
        assertTime(0.seconds) { bucket.acquireToken() }
        assertEquals(0, bucket.capacity)
        val result = runCatching { bucket.acquireToken() }
        assertIs<RetryCapacityExceededException>(result.exceptionOrNull())
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
private fun TestScope.tokenBucket(
    useCircuitBreakerMode: Boolean = false,
    initialTryCost: Int = 0,
    initialTrySuccessIncrement: Int = 1,
    maxCapacity: Int = 10,
    refillUnitsPerSecond: Int = 10,
    retryCost: Int = DEFAULT_RETRY_COST,
    timeoutRetryCost: Int = DEFAULT_TIMEOUT_RETRY_COST,
    timeSource: TimeSource = testTimeSource,
): StandardRetryTokenBucket {
    val config = StandardRetryTokenBucket.Config {
        this.useCircuitBreakerMode = useCircuitBreakerMode
        this.initialTryCost = initialTryCost
        this.initialTrySuccessIncrement = initialTrySuccessIncrement
        this.maxCapacity = maxCapacity
        this.refillUnitsPerSecond = refillUnitsPerSecond
        this.retryCost = retryCost
        this.timeoutRetryCost = timeoutRetryCost
    }
    return StandardRetryTokenBucket(config, timeSource)
}
