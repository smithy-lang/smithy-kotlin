/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.RetryErrorType
import aws.smithy.kotlin.runtime.time.ManualClock
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class StandardRetryTokenBucketTest {
    companion object {
        private val DefaultOptions = StandardRetryTokenBucketOptions(
            maxCapacity = 10,
            refillUnitsPerSecond = 10,
            retryCost = 2,
            timeoutRetryCost = 3,
            initialTryCost = 0,
            initialTrySuccessIncrement = 1,
        )
    }

    @Test
    fun testWaitForCapacity() = runBlockingTest {
        // A bucket that only allows one initial try per second
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 10))

        assertEquals(10, bucket.capacity)
        assertTime(0) { bucket.acquireToken() }
        assertEquals(0, bucket.capacity)
        assertTime(1000) { bucket.acquireToken() }
    }

    @Test
    fun testReturnCapacityOnSuccess() = runBlockingTest {
        // A bucket that costs capacity for an initial try and doesn't return the same capacity (for easy measuring)
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 5, initialTrySuccessIncrement = 3))

        assertEquals(10, bucket.capacity)
        val initialToken = assertTime(0) { bucket.acquireToken() }
        assertEquals(5, bucket.capacity)
        assertTime(0) { initialToken.notifySuccess() }
        assertEquals(8, bucket.capacity)
    }

    @Test
    fun testNoCapacityChangeOnFailure() = runBlockingTest {
        // A bucket that costs capacity for an initial try
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 1))

        assertEquals(10, bucket.capacity)
        val initialToken = assertTime(0) { bucket.acquireToken() }
        assertEquals(9, bucket.capacity)
        assertTime(0) { initialToken.notifyFailure() }
        assertEquals(9, bucket.capacity)
    }

    @Test
    fun testRetryCapacityAdjustments() = runBlockingTest {
        mapOf(
            RetryErrorType.Throttling to DefaultOptions.timeoutRetryCost,
            RetryErrorType.Timeout to DefaultOptions.timeoutRetryCost,
            RetryErrorType.ClientSide to DefaultOptions.retryCost,
            RetryErrorType.ServerSide to DefaultOptions.retryCost,
        ).forEach { (errorType, cost) ->
            val bucket = StandardRetryTokenBucket(DefaultOptions)

            assertEquals(10, bucket.capacity)
            val initialToken = assertTime(0) { bucket.acquireToken() }
            assertEquals(10, bucket.capacity)
            val retryToken = assertTime(0) { initialToken.scheduleRetry(errorType) }
            assertEquals(10 - cost, bucket.capacity)
        }
    }

    @Test
    @ExperimentalTime
    fun testRefillOverTime() = runBlockingTest {
        val clock = ManualClock()

        // A bucket that costs capacity for an initial try
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 5), clock)

        assertEquals(10, bucket.capacity)
        assertTime(0) { bucket.acquireToken() }
        assertEquals(5, bucket.capacity)

        // Refill rate is 10/s == 1/100ms so after 250ms we should have 2 more tokens.
        clock.advance(Duration.milliseconds(250))

        assertTime(0) { bucket.acquireToken() }
        assertEquals(2, bucket.capacity) // We had 5, 2 refilled, and then we decremented 5 more.
    }
}
