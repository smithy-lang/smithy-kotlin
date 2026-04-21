/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LegacyRetryStrategyTest {
    @Test
    fun testInitialSuccess() = runTest {
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = LegacyRetryStrategy {
            tokenBucket = bucket
            delayProvider = delayer
        }
        val policy = StringRetryPolicy()

        val result = retryer.retry(policy, block(policy, bucket, delayer, "success"))

        assertEquals(Outcome.Response(1, "success"), result)
        val token = bucket.lastTokenAcquired!!
        assertTrue(token.isSuccess)
    }

    @Test
    fun testRetryableFailures() = runTest {
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = LegacyRetryStrategy {
            maxAttempts = 10
            tokenBucket = bucket
            delayProvider = delayer
        }
        val policy = StringRetryPolicy()

        val result = retryer.retry(
            policy,
            block(
                policy,
                bucket,
                delayer,
                "client-error",
                "server-error",
                "transient",
                "throttled",
                "success",
            ),
        )

        assertEquals(Outcome.Response(5, "success"), result)
        val token = bucket.lastTokenAcquired!!
        assertTrue(token.nextToken!!.nextToken!!.nextToken!!.nextToken!!.isSuccess)
    }

    @Test
    fun testNonretryableFailureFromException() = runTest {
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = LegacyRetryStrategy {
            tokenBucket = bucket
            delayProvider = delayer
        }
        val policy = StringRetryPolicy()

        val result = runCatching {
            retryer.retry(policy, block(policy, bucket, delayer, IllegalStateException()))
        }

        assertIs<IllegalStateException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.isFailure)
    }

    @Test
    fun testNonretryableFailureFromResult() = runTest {
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = LegacyRetryStrategy {
            tokenBucket = bucket
            delayProvider = delayer
        }
        val policy = StringRetryPolicy()

        val result = runCatching {
            retryer.retry(policy, block(policy, bucket, delayer, "fail"))
        }

        val ex = assertIs<RetryFailureException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")
        assertEquals(1, ex.attempts)
        assertEquals("fail", ex.lastResponse)

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.isFailure)
    }

    @Test
    fun testTooManyAttemptsFromException() = runTest {
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = LegacyRetryStrategy {
            tokenBucket = bucket
            delayProvider = delayer
        }
        val policy = StringRetryPolicy()

        val result = runCatching {
            retryer.retry(
                policy,
                block(
                    policy,
                    bucket,
                    delayer,
                    ConcurrentModificationException(),
                    ConcurrentModificationException(),
                    ConcurrentModificationException(),
                ),
            )
        }

        assertIs<ConcurrentModificationException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.nextToken!!.nextToken!!.isFailure)
    }

    @Test
    fun testTooManyAttemptsFromResult() = runTest {
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = LegacyRetryStrategy {
            tokenBucket = bucket
            delayProvider = delayer
        }
        val policy = StringRetryPolicy()

        val result = runCatching {
            retryer.retry(
                policy,
                block(
                    policy,
                    bucket,
                    delayer,
                    "client-error",
                    "server-error",
                    "transient",
                ),
            )
        }

        val ex = assertIs<TooManyAttemptsException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")
        assertEquals(3, ex.attempts)
        assertEquals("transient", ex.lastResponse)

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.nextToken!!.nextToken!!.isFailure)
    }
}
