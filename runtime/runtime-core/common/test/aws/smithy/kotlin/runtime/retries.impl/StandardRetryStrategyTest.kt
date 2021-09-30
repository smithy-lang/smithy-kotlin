/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.*

class StandardRetryStrategyTest {
    @Test
    fun testInitialSuccess() = runBlockingTest {
        val options = StandardRetryStrategyOptions.Default
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = StandardRetryStrategy(options, bucket, delayer)
        val policy = StringRetryPolicy()

        val result = retryer.retry(policy, block(policy, bucket, delayer, "success"))

        assertEquals("success", result)
        val token = bucket.lastTokenAcquired!!
        assertTrue(token.isSuccess)
    }

    @Test
    fun testRetryableFailures() = runBlockingTest {
        val options = StandardRetryStrategyOptions.Default
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = StandardRetryStrategy(options, bucket, delayer)
        val policy = StringRetryPolicy()

        val result = retryer.retry(
            policy,
            block(
                policy,
                bucket,
                delayer,
                "client-error",
                "server-error",
                "timeout",
                "throttled",
                "success",
            )
        )

        assertEquals("success", result)
        val token = bucket.lastTokenAcquired!!
        assertTrue(token.nextToken!!.nextToken!!.nextToken!!.nextToken!!.isSuccess)
    }

    @Test
    fun testNonretryableFailure() = runBlockingTest {
        val options = StandardRetryStrategyOptions.Default
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = StandardRetryStrategy(options, bucket, delayer)
        val policy = StringRetryPolicy()

        val result = runCatching {
            retryer.retry(policy, block(policy, bucket, delayer, "fail"))
        }

        val ex = result.exceptionOrNull() as RetryFailureException
        assertEquals(1, ex.attempts)
        assertEquals("fail", ex.lastResponse)

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.isFailure)
    }

    @Test
    fun testTooManyAttempts() = runBlockingTest {
        val options = StandardRetryStrategyOptions.Default.copy(maxAttempts = 3)
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = StandardRetryStrategy(options, bucket, delayer)
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
                    "timeout",
                )
            )
        }

        val ex = result.exceptionOrNull() as TooManyAttemptsException
        assertEquals(3, ex.attempts)
        assertEquals("timeout", ex.lastResponse)

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.nextToken!!.nextToken!!.isFailure)
    }

    @Test
    fun testTooLong() = runBlockingTest {
        val options = StandardRetryStrategyOptions.Default.copy(maxTimeMs = 1_000)
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = StandardRetryStrategy(options, bucket, delayer)
        val policy = StringRetryPolicy()

        val result = runCatching {
            retryer.retry(policy) {
                delay(2_000)
                "This will never run!"
            }
        }

        val ex = result.exceptionOrNull() as TimedOutException
        assertEquals(1, ex.attempts)
        assertNull(ex.lastResponse)
        assertNull(ex.lastException)

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.isFailure)
    }
}

fun block(
    retryPolicy: RetryPolicy<String>,
    bucket: RecordingTokenBucket,
    delayer: RecordingDelayer,
    vararg results: String,
): suspend () -> String {
    val container = object {
        var index = 0
        var lastToken: RecordingToken? = null

        suspend fun doIt(): String {
            val expectedDelayAttempt = if (index < 1) null else index
            assertEquals(expectedDelayAttempt, delayer.lastAttempt)

            lastToken = if (index == 0) {
                bucket.lastTokenAcquired!!
            } else {
                val expectedRetryDirective = retryPolicy.evaluate(Result.success(results[index - 1]))
                val expectedRetryError = (expectedRetryDirective as RetryDirective.RetryError).reason
                assertEquals(expectedRetryError, lastToken!!.retryReason)
                lastToken!!.nextToken!!
            }

            return results[index++]
        }
    }

    return container::doIt
}

class RecordingTokenBucket : RetryTokenBucket {
    var lastTokenAcquired: RecordingToken? = null

    override suspend fun acquireToken(): RetryToken = RecordingToken().also { lastTokenAcquired = it }
}

class RecordingToken : RetryToken {
    var isFailure: Boolean = false
    var isSuccess: Boolean = false
    var retryReason: RetryErrorType? = null
    var nextToken: RecordingToken? = null

    override suspend fun notifyFailure() {
        requireCleanState()
        isFailure = true
    }

    override suspend fun notifySuccess() {
        requireCleanState()
        isSuccess = true
    }

    override suspend fun scheduleRetry(reason: RetryErrorType): RetryToken {
        requireCleanState()
        retryReason = reason
        return RecordingToken().also { nextToken = it }
    }

    private fun requireCleanState() {
        require(!isFailure) { "This token has already been marked failed" }
        require(!isSuccess) { "This token has already been marked succeeded" }
        require(retryReason == null) { "This token has already been retried for $retryReason" }
        require(nextToken == null) { "This token already returned a new token" }
    }
}

class RecordingDelayer : BackoffDelayer {
    var lastAttempt: Int? = null
    override suspend fun backoff(attempt: Int) {
        val expectedLastAttempt = if (attempt == 1) null else attempt - 1
        require(lastAttempt == expectedLastAttempt) {
            "This delayer was called for attempt $attempt but the prior attempt was $lastAttempt"
        }
        lastAttempt = attempt
    }
}

class StringRetryPolicy : RetryPolicy<String> {
    override fun evaluate(result: Result<String>): RetryDirective = when (result.getOrNull()) {
        "success" -> RetryDirective.TerminateAndSucceed
        "fail" -> RetryDirective.TerminateAndFail
        "client-error" -> RetryDirective.RetryError(RetryErrorType.ClientSide)
        "server-error" -> RetryDirective.RetryError(RetryErrorType.ServerSide)
        "timeout" -> RetryDirective.RetryError(RetryErrorType.Timeout)
        "throttled" -> RetryDirective.RetryError(RetryErrorType.Throttling)
        else -> throw IllegalArgumentException("Bad value in policy evaluation: $result", result.exceptionOrNull())
    }
}
