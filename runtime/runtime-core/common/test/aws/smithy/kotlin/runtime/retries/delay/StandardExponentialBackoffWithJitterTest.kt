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

class StandardExponentialBackoffWithJitterTest {
    @Test
    fun testDefaults() {
        val config = StandardExponentialBackoffWithJitter.Config(StandardExponentialBackoffWithJitter.Config.Builder())
        assertEquals(50.milliseconds, config.initialDelay)
        assertEquals(2.0, config.scaleFactor)
        assertEquals(1.0, config.jitter)
        assertEquals(20.seconds, config.maxBackoff)
    }

    @Test
    fun testNonThrottlingScaling() = runTest {
        val delayer = StandardExponentialBackoffWithJitter {
            initialDelay = 50.milliseconds
            scaleFactor = 2.0
            jitter = 0.0
            maxBackoff = Duration.INFINITE
        }
        // 50, 100, 200, 400, 800
        assertEquals(listOf(50, 100, 200, 400, 800), backoffSeries(5, delayer, RetryErrorType.ServerSide))
    }

    @Test
    fun testThrottlingBase() = runTest {
        val delayer = StandardExponentialBackoffWithJitter {
            jitter = 0.0
            maxBackoff = Duration.INFINITE
        }
        // Throttling base = 1000ms: 1000, 2000, 4000
        assertEquals(listOf(1000, 2000, 4000), backoffSeries(3, delayer, RetryErrorType.Throttling))
    }

    @Test
    fun testDynamoDbBase() = runTest {
        val delayer = StandardExponentialBackoffWithJitter {
            jitter = 0.0
            maxBackoff = Duration.INFINITE
        }
        // DynamoDB base = 25ms: 25, 50, 100
        assertEquals(listOf(25, 50, 100), backoffSeries(3, delayer, RetryErrorType.ServerSide, "dynamodb"))
    }

    @Test
    fun testDynamoDbStreamsBase() = runTest {
        val delayer = StandardExponentialBackoffWithJitter {
            jitter = 0.0
            maxBackoff = Duration.INFINITE
        }
        assertEquals(listOf(25, 50, 100), backoffSeries(3, delayer, RetryErrorType.ServerSide, "DynamoDB Streams"))
    }

    @Test
    fun testMaxBackoff() = runTest {
        val delayer = StandardExponentialBackoffWithJitter {
            jitter = 0.0
            maxBackoff = 150.milliseconds
        }
        // 50, 100, 150 (capped), 150 (capped)
        assertEquals(listOf(50, 100, 150, 150), backoffSeries(4, delayer, RetryErrorType.ServerSide))
    }

    @Test
    fun testJitter() = runTest {
        val delayer = StandardExponentialBackoffWithJitter {
            jitter = 1.0
            maxBackoff = Duration.INFINITE
        }
        // With full jitter, delay should be in [0, base * 2^(attempt-1)]
        backoffSeries(4, delayer, RetryErrorType.ServerSide)
            .zip(listOf(0..50, 0..100, 0..200, 0..400))
            .forEach { (actualMs, rangeMs) ->
                assertTrue(actualMs in rangeMs, "Actual ms $actualMs was not in expected range $rangeMs")
            }
    }

    @Test
    fun testFallbackBackoff() = runTest {
        // backoff(attempt) without error type should still work
        val delayer = StandardExponentialBackoffWithJitter {
            jitter = 0.0
            maxBackoff = Duration.INFINITE
        }
        val series = (1..3).map { idx -> measure { delayer.backoff(idx) } }.map { it.first }
        assertEquals(listOf(50, 100, 200), series)
    }

    @Test
    fun testRetryAfterHonored() = runTest {
        // x-amz-retry-after: 1500 → delay = 1500ms (within [50, 50+5000] range)
        val delayer = StandardExponentialBackoffWithJitter { jitter = 0.0 }
        val (ms, _) = measure { withContext(RetryContext().apply { retryAfter = 1500L.milliseconds }) { delayer.backoff(1, RetryErrorType.ServerSide) } }
        assertEquals(1500, ms)
    }

    @Test
    fun testRetryAfterClampedToMinimum() = runTest {
        // x-amz-retry-after: 0 → clamped to t_i (50ms for attempt 1)
        val delayer = StandardExponentialBackoffWithJitter { jitter = 0.0 }
        val (ms, _) = measure { withContext(RetryContext().apply { retryAfter = 0L.milliseconds }) { delayer.backoff(1, RetryErrorType.ServerSide) } }
        assertEquals(50, ms)
    }

    @Test
    fun testRetryAfterClampedToMaximum() = runTest {
        // x-amz-retry-after: 10000 → clamped to t_i + 5000 (50 + 5000 = 5050ms)
        val delayer = StandardExponentialBackoffWithJitter { jitter = 0.0 }
        val (ms, _) = measure { withContext(RetryContext().apply { retryAfter = 10000L.milliseconds }) { delayer.backoff(1, RetryErrorType.ServerSide) } }
        assertEquals(5050, ms)
    }

    @Test
    fun testRetryAfterIgnoredWhenNull() = runTest {
        // No header → normal backoff (50ms)
        val delayer = StandardExponentialBackoffWithJitter { jitter = 0.0 }
        val (ms, _) = measure { delayer.backoff(1, RetryErrorType.ServerSide) }
        assertEquals(50, ms)
    }
}

private suspend fun TestScope.backoffSeries(
    times: Int,
    delayer: StandardExponentialBackoffWithJitter,
    errorType: RetryErrorType,
    serviceName: String? = null,
): List<Int> = (1..times)
    .map { idx -> measure { delayer.backoff(idx, errorType, serviceName) } }
    .map { it.first }
