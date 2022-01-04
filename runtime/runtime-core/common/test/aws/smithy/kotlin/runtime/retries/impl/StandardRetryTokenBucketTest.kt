/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.RetryCapacityExceededException
import aws.smithy.kotlin.runtime.retries.RetryErrorType
import aws.smithy.kotlin.runtime.time.ManualClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

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
    fun testWaitForCapacity() = runBlockingTest {
        // A bucket that only allows one initial try per second
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 10))

        assertEquals(10, bucket.capacity)
        assertTime(0) { bucket.acquireToken() }
        assertEquals(0, bucket.capacity)
        assertTime(1000) { bucket.acquireToken() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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

    @OptIn(ExperimentalCoroutinesApi::class)
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

    @OptIn(ExperimentalCoroutinesApi::class)
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
            assertTime(0) { initialToken.scheduleRetry(errorType) }
            assertEquals(10 - cost, bucket.capacity)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    @Test
    fun testRefillOverTime() = runBlockingTest {
        val clock = ManualClock()

        // A bucket that costs capacity for an initial try
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 5), clock)

        assertEquals(10, bucket.capacity)
        assertTime(0) { bucket.acquireToken() }
        assertEquals(5, bucket.capacity)

        // Refill rate is 10/s == 1/100ms so after 250ms we should have 2 more tokens.
        clock.advance(250.milliseconds)

        assertTime(0) { bucket.acquireToken() }
        assertEquals(2, bucket.capacity) // We had 5, 2 refilled, and then we decremented 5 more.
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCircuitBreakerMode() = runBlockingTest {
        // A bucket that only allows one initial try per second
        val bucket = StandardRetryTokenBucket(DefaultOptions.copy(initialTryCost = 10, circuitBreakerMode = true))

        assertEquals(10, bucket.capacity)
        assertTime(0) { bucket.acquireToken() }
        assertEquals(0, bucket.capacity)
        val result = runCatching { bucket.acquireToken() }
        assertIs<RetryCapacityExceededException>(result.exceptionOrNull())
    }
}
