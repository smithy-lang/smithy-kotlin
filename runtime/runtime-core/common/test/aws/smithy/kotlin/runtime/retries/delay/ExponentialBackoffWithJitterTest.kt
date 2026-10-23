/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.RetryContext
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ExponentialBackoffWithJitterTest {
    @Test
    fun testDefaults() {
        val config = ExponentialBackoffWithJitter.Config(
            ExponentialBackoffWithJitter.Config.Builder().apply {
                initialDelay = 50.milliseconds
                scaleFactor = 2.0
            },
        )
        assertEquals(50.milliseconds, config.initialDelay)
        assertEquals(2.0, config.scaleFactor)
        assertEquals(1.0, config.jitter)
        assertEquals(20.seconds, config.maxBackoff)
        assertEquals(1.seconds, config.throttlingBaseDelay)
        assertEquals(5.seconds, config.retryAfterMaxOvershoot)
    }

    @Test
    fun testScaling() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 10.milliseconds
            scaleFactor = 2.0 // Make the numbers easy for tests
            jitter = 0.0 // Disable jitter for this test
            maxBackoff = Duration.INFINITE // Effectively disable max backoff
        }
        assertEquals(listOf(10, 20, 40, 80, 160, 320), backoffSeries(6, delayer))
    }

    @Test
    fun testJitter() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 10.milliseconds
            scaleFactor = 2.0 // Make the numbers easy for tests
            jitter = 0.6 // 60% jitter for this test
            maxBackoff = Duration.INFINITE // Effectively disable max backoff
        }
        backoffSeries(6, delayer)
            .zip(listOf(4..10, 8..20, 16..40, 32..80, 64..160, 128..320))
            .forEach { (actualMs, rangeMs) ->
                assertTrue(actualMs in rangeMs, "Actual ms $actualMs was not in expected range $rangeMs")
            }
    }

    @Test
    fun testMaxBackoff() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 10.milliseconds
            scaleFactor = 2.0 // Make the numbers easy for tests
            jitter = 0.0 // Disable jitter for this test
            maxBackoff = 100.milliseconds
        }
        assertEquals(listOf(10, 20, 40, 80, 100, 100), backoffSeries(6, delayer))
    }

    @Test
    fun testNonThrottlingScaling() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 50.milliseconds
            scaleFactor = 2.0
            jitter = 0.0
            maxBackoff = Duration.INFINITE
        }
        assertEquals(listOf(50, 100, 200, 400, 800), backoffSeries(5, delayer, RetryErrorType.ServerSide))
    }

    @Test
    fun testThrottlingBase() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 50.milliseconds
            scaleFactor = 2.0
            jitter = 0.0
            maxBackoff = Duration.INFINITE
        }
        assertEquals(listOf(1000, 2000, 4000), backoffSeries(3, delayer, RetryErrorType.Throttling))
    }

    @Test
    fun testRetryAfterHonored() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 50.milliseconds
            scaleFactor = 2.0
            jitter = 0.0
        }
        val ctx = RetryContext().apply { retryAfter = 1500L.milliseconds }
        val (ms, _) = measure { withContext(ctx) { delayer.backoff(1) } }
        assertEquals(1500, ms)
    }

    @Test
    fun testRetryAfterClampedToMinimum() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 50.milliseconds
            scaleFactor = 2.0
            jitter = 0.0
        }
        val ctx = RetryContext().apply { retryAfter = 0L.milliseconds }
        val (ms, _) = measure { withContext(ctx) { delayer.backoff(1) } }
        assertEquals(50, ms)
    }

    @Test
    fun testRetryAfterClampedToMaximum() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 50.milliseconds
            scaleFactor = 2.0
            jitter = 0.0
        }
        val ctx = RetryContext().apply { retryAfter = 10000L.milliseconds }
        val (ms, _) = measure { withContext(ctx) { delayer.backoff(1) } }
        assertEquals(5050, ms)
    }

    @Test
    fun testRetryAfterIgnoredWhenNull() = runTest {
        val delayer = ExponentialBackoffWithJitter {
            initialDelay = 50.milliseconds
            scaleFactor = 2.0
            jitter = 0.0
        }
        val (ms, _) = measure { delayer.backoff(1) }
        assertEquals(50, ms)
    }
}

private suspend fun TestScope.backoffSeries(
    times: Int,
    delayer: ExponentialBackoffWithJitter,
    errorType: RetryErrorType? = null,
): List<Int> {
    val ctx = errorType?.let { RetryContext().apply { this.errorType = it } }
    return (1..times)
        .map { idx ->
            if (ctx != null) {
                measure { withContext(ctx) { delayer.backoff(idx) } }
            } else {
                measure { delayer.backoff(idx) }
            }
        }
        .map { it.first }
}
