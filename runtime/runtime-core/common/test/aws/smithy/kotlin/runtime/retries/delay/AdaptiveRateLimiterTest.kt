/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveRateLimiterTest {
    @Test
    fun testAcquireNoOpWhenThrottlingDisabled() = runTest {
        val limiter = rateLimiter()
        assertTime(0.seconds) { limiter.acquire(1) }
    }

    @Test
    fun testAcquireImmediateWhenCapacityAvailable() = runTest {
        val limiter = rateLimiter(throttling = true, fillRate = 10.0, maxCapacity = 10.0)
        testScheduler.advanceTimeBy(2_000)
        assertTime(0.seconds) { limiter.acquire(1) }
    }

    @Test
    fun testAcquireSleepsWhenCapacityInsufficient() = runTest {
        val limiter = rateLimiter(throttling = true, fillRate = 1.0, maxCapacity = 10.0)
        assertTime(5.seconds) { limiter.acquire(5) }
    }

    @Test
    fun testUpdateNotBlockedByAcquireSleep() = runTest {
        // acquiring 10 tokens requires ~10s of sleeping
        val limiter = rateLimiter(throttling = true, fillRate = 1.0, maxCapacity = 10.0)

        val acquireJob = async { limiter.acquire(10) }
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()

        // acquire is still sleeping — needs 9 more seconds
        assertFalse(acquireJob.isCompleted)

        // update() should complete immediately — not blocked by the sleeping acquire job
        val updateJob = async { limiter.update(null) }
        testScheduler.runCurrent()
        assertTrue(updateJob.isCompleted, "Expected update() to complete while acquire() is sleeping")

        // Restore rates after update() changed them, then assert acquire finishes in the remaining ~9s
        limiter.refillUnitsPerSecond = 1.0
        limiter.maxCapacity = 10.0
        assertTime(9.seconds) { acquireJob.await() }
    }

    private suspend fun TestScope.rateLimiter(
        throttling: Boolean = false,
        fillRate: Double = 0.0,
        maxCapacity: Double = 0.0,
    ): AdaptiveRateLimiter {
        val config = AdaptiveRateLimiter.Config.Default
        val timeSource = testTimeSource
        val rateLimiter = AdaptiveRateLimiter(
            config,
            timeSource,
            AdaptiveRateMeasurer(config, timeSource),
            CubicRateCalculator(config, timeSource),
        )
        if (throttling) {
            rateLimiter.update(RetryErrorType.Throttling)
            rateLimiter.refillUnitsPerSecond = fillRate
            rateLimiter.maxCapacity = maxCapacity
        }
        return rateLimiter
    }
}
