/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

class StandardRetryTokenBucketTest {
    companion object {
        private val DefaultOptions = StandardRetryTokenBucketOptions(
            maxCapacity = 10,
            refillUnitsPerSecond = 10,
            circuitBreakerMode = false,
            retryCost = 2,
            timeoutRetryCost = 3,
            initialTryCost = 0,
            initialTrySuccessIncrement = 1,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testWaitForCapacity() = runTest {
        // A bucket that only allows one initial try per second
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 10))

        assertEquals(10, bucket.capacity)
        assertTime(0) { bucket.acquireToken() }
        assertEquals(0, bucket.capacity)
        assertTime(1000) { bucket.acquireToken() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testReturnCapacityOnSuccess() = runTest {
        // A bucket that costs capacity for an initial try and doesn't return the same capacity (for easy measuring)
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 5, initialTrySuccessIncrement = 3))

        assertEquals(10, bucket.capacity)
        val initialToken = assertTime(0) { bucket.acquireToken() }
        assertEquals(5, bucket.capacity)
        assertTime(0) { initialToken.notifySuccess() }
        assertEquals(8, bucket.capacity)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testNoCapacityChangeOnFailure() = runTest {
        // A bucket that costs capacity for an initial try
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 1))

        assertEquals(10, bucket.capacity)
        val initialToken = assertTime(0) { bucket.acquireToken() }
        assertEquals(9, bucket.capacity)
        assertTime(0) { initialToken.notifyFailure() }
        assertEquals(9, bucket.capacity)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRetryCapacityAdjustments() = runTest {
        mapOf(
            RetryErrorType.Throttling to DefaultOptions.timeoutRetryCost,
            RetryErrorType.Transient to DefaultOptions.timeoutRetryCost,
            RetryErrorType.ClientSide to DefaultOptions.retryCost,
            RetryErrorType.ServerSide to DefaultOptions.retryCost,
        ).forEach { (errorType, cost) ->
            val bucket = StandardRetryTokenBucket(DefaultOptions)

            assertEquals(10, bucket.capacity)
            val initialToken = assertTime(0) { bucket.acquireToken() }
            assertEquals(10, bucket.capacity)
            assertTime(0) { initialToken.scheduleRetry(errorType) }
            assertEquals(10 - cost, bucket.capacity)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    @Test
    fun testRefillOverTime() = runTest {
        val timeSource = TestTimeSource()

        // A bucket that costs capacity for an initial try
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 5), timeSource)

        assertEquals(10, bucket.capacity)
        assertTime(0) { bucket.acquireToken() }
        assertEquals(5, bucket.capacity)

        // Refill rate is 10/s == 1/100ms so after 250ms we should have 2 more tokens.
        timeSource += 250.milliseconds

        assertTime(0) { bucket.acquireToken() }
        assertEquals(2, bucket.capacity) // We had 5, 2 refilled, and then we decremented 5 more.
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCircuitBreakerMode() = runTest {
        // A bucket that only allows one initial try per second
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 10, circuitBreakerMode = true))

        assertEquals(10, bucket.capacity)
        assertTime(0) { bucket.acquireToken() }
        assertEquals(0, bucket.capacity)
        val result = runCatching { bucket.acquireToken() }
        assertIs<RetryCapacityExceededException>(result.exceptionOrNull())
    }
}
