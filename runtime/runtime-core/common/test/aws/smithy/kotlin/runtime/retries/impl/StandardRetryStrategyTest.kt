/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class StandardRetryStrategyTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testInitialSuccess() = runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRetryableFailures() = runTest {
        val options = StandardRetryStrategyOptions.Default.copy(maxAttempts = 10)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testNonretryableFailureFromException() = runTest {
        val options = StandardRetryStrategyOptions.Default
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = StandardRetryStrategy(options, bucket, delayer)
        val policy = StringRetryPolicy()

        val result = runCatching {
            retryer.retry(policy, block(policy, bucket, delayer, IllegalStateException()))
        }

        assertIs<IllegalStateException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.isFailure)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testNonretryableFailureFromResult() = runTest {
        val options = StandardRetryStrategyOptions.Default
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = StandardRetryStrategy(options, bucket, delayer)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTooManyAttemptsFromException() = runTest {
        val options = StandardRetryStrategyOptions.Default
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
                    ConcurrentModificationException(),
                    ConcurrentModificationException(),
                    ConcurrentModificationException(),
                )
            )
        }

        assertIs<ConcurrentModificationException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.nextToken!!.nextToken!!.isFailure)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTooManyAttemptsFromResult() = runTest {
        val options = StandardRetryStrategyOptions.Default
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

        val ex = assertIs<TooManyAttemptsException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")
        assertEquals(3, ex.attempts)
        assertEquals("timeout", ex.lastResponse)

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.nextToken!!.nextToken!!.isFailure)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTooLongFromException() = runTest {
        val options = StandardRetryStrategyOptions.Default.copy(maxTime = 1_500.milliseconds)
        val bucket = RecordingTokenBucket()
        val delayer = RecordingDelayer()
        val retryer = StandardRetryStrategy(options, bucket, delayer)
        val policy = StringRetryPolicy()

        val result = runCatching {
            retryer.retry(policy) {
                delay(1_000)
                throw ConcurrentModificationException()
            }
        }

        assertIs<ConcurrentModificationException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")

        val token = bucket.lastTokenAcquired!!
        assertTrue(token.nextToken!!.isFailure)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTooLongFromResult() = runTest {
        val options = StandardRetryStrategyOptions.Default.copy(maxTime = 1_000.milliseconds)
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

        val ex = assertIs<TimedOutException>(result.exceptionOrNull(), "Unexpected ${result.exceptionOrNull()}")
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
    vararg results: Any,
): suspend () -> String {
    val container = object {
        var currentIndex = 0
        var lastToken: RecordingToken? = null

        suspend fun doIt(): String {
            val expectedDelayAttempt = if (currentIndex < 1) null else currentIndex
            assertEquals(expectedDelayAttempt, delayer.lastAttempt)

            lastToken = if (currentIndex == 0) {
                bucket.lastTokenAcquired!!
            } else {
                val expectedRetryDirective = retryPolicy.evaluate(wrap(currentIndex - 1))
                val expectedRetryError = assertIs<RetryDirective.RetryError>(
                    expectedRetryDirective,
                    "Unexpected $expectedRetryDirective"
                )
                assertEquals(expectedRetryError.reason, lastToken!!.retryReason)
                lastToken!!.nextToken!!
            }

            return wrap(currentIndex++).getOrThrow()
        }

        private fun wrap(atIndex: Int): Result<String> {
            val result = results[atIndex]
            return if (result is Throwable) Result.failure(result) else Result.success(result.toString())
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

class RecordingDelayer : DelayProvider {
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
        null -> {
            assertNotNull(result.exceptionOrNull()) // If the value is null, this must be an exception
            when (result.exceptionOrNull()) {
                is ConcurrentModificationException -> RetryDirective.RetryError(RetryErrorType.ClientSide)
                else -> RetryDirective.TerminateAndFail
            }
        }
        else -> throw IllegalArgumentException("Bad value in policy evaluation: $result", result.exceptionOrNull())
    }
}
