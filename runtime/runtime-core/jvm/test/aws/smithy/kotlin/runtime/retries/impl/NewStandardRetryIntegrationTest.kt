/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.RetryContext
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.delay.ExponentialBackoffWithJitter
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucket
import aws.smithy.kotlin.runtime.retries.getOrThrow
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.util.TestPlatformProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Integration tests for the standard retry strategy behavior.
 * These tests configure the standard retry constants (retryCost=14, timeoutRetryCost=5)
 * and use [ExponentialBackoffWithJitter] (initialDelay=50ms, scaleFactor=2.0).
 */
class NewStandardRetryIntegrationTest {
    private val platform = TestPlatformProvider(props = mapOf("smithy.newRetries2026" to "true"))
    private val defaultDelayConfig = ExponentialBackoffWithJitter.Config(ExponentialBackoffWithJitter.Config.Builder(platform))

    private fun sepTokenBucket(maxCapacity: Int? = null): StandardRetryTokenBucket {
        val builder = StandardRetryTokenBucket.Config.Builder(platform)
        maxCapacity?.let { builder.maxCapacity = it }
        return StandardRetryTokenBucket(StandardRetryTokenBucket.Config(builder))
    }

    private fun buildStrategy(given: NewStandardGiven, tokenBucket: StandardRetryTokenBucket) = StandardRetryStrategy {
        given.maxAttempts?.let { maxAttempts = it }
        if (given.longPolling) enableLongPollingBackoff = true
        this.tokenBucket = tokenBucket
        delayProvider {
            initialDelay = defaultDelayConfig.initialDelay
            scaleFactor = defaultDelayConfig.scaleFactor
            if (given.exponentialBase == 1.0) jitter = 0.0
            given.maxBackoffTime?.let { maxBackoff = (it * 1000).toLong().milliseconds }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testIntegrationCases() = runTest {
        val testCases = newStandardRetryIntegrationTestCases.deserializeYaml(NewStandardRetryTestCase.serializer())
        testCases.forEach { (name, tc) ->
            val tokenBucket = sepTokenBucket(tc.given.initialRetryTokens)
            val retryer = buildStrategy(tc.given, tokenBucket)

            val policy = NewSepRetryPolicy(tc.responses)
            val retryCtx = RetryContext()
            val block = object {
                var index = 0
                suspend fun doIt(): Ok {
                    if (index > 0) {
                        assertEquals(
                            tc.responses[index - 1].expected.retryQuota,
                            tokenBucket.capacity,
                            "Quota mismatch after response ${index - 1} in '$name'",
                        )
                    }
                    val resp = tc.responses[index++]
                    currentCoroutineContext()[RetryContext]!!.retryAfter = resp.response.parseRetryAfter()
                    return resp.response.toResult()
                }
            }::doIt

            val startTimeMs = currentTime
            val result = runCatching { withContext(retryCtx) { retryer.retry(policy, block) } }
            val totalDelayMs = currentTime - startTimeMs

            val finalState = tc.responses.last().expected
            when (finalState.outcome) {
                NewStandardTestOutcome.Success ->
                    assertEquals(Ok, result.getOrThrow().getOrThrow(), "Unexpected outcome for $name")

                NewStandardTestOutcome.MaxAttemptsExceeded -> {
                    val e = assertFailsWith<HttpCodeException>("Expected exception for $name") {
                        result.getOrThrow()
                    }
                    assertEquals(tc.responses.last().response.statusCode, e.code, "Unexpected error code for $name")
                }

                NewStandardTestOutcome.RetryQuotaExceeded -> {
                    val e = assertFailsWith<HttpCodeException>("Expected exception for $name") {
                        result.getOrThrow()
                    }
                    assertEquals(tc.responses.last().response.statusCode, e.code, "Unexpected error code for $name")
                    assertTrue("Expected retry capacity message in exception for $name") {
                        "Insufficient client capacity to attempt retry" in e.message
                    }
                }

                else -> fail("Unexpected outcome for $name: ${finalState.outcome}")
            }

            val expectedDelayMs = tc.responses
                .mapNotNull { it.expected.delay }
                .sumOf { (it * 1000).toLong() }

            if (finalState.outcome == NewStandardTestOutcome.RetryQuotaExceeded) {
                assertTrue(
                    expectedDelayMs <= totalDelayMs,
                    "Unexpected delay for $name. Expected at least ${expectedDelayMs}ms but was ${totalDelayMs}ms",
                )
            } else {
                assertEquals(expectedDelayMs, totalDelayMs, "Unexpected delay for $name")
            }

            assertEquals(finalState.retryQuota, tokenBucket.capacity, "Final quota mismatch for $name")
        }
    }

    /**
     * Retry quota recovery after successful responses.
     * Multi-invocation test: responses are split on `success` outcomes, each group is a separate retry() call.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testMultiInvocationCases() = runTest {
        val testCases = newStandardRetryMultiInvocationTestCases.deserializeYaml(NewStandardRetryTestCase.serializer())
        testCases.forEach { (name, tc) ->
            val tokenBucket = sepTokenBucket(tc.given.initialRetryTokens)
            val retryer = buildStrategy(tc.given, tokenBucket)

            // Split responses into invocations at each success boundary
            val invocations = mutableListOf<List<NewStandardResponseAndExpectation>>()
            var current = mutableListOf<NewStandardResponseAndExpectation>()
            for (resp in tc.responses) {
                current.add(resp)
                if (resp.expected.outcome == NewStandardTestOutcome.Success) {
                    invocations.add(current)
                    current = mutableListOf()
                }
            }
            if (current.isNotEmpty()) invocations.add(current)

            for (invocation in invocations) {
                val policy = NewSepRetryPolicy(invocation)
                var index = 0
                withContext(RetryContext()) {
                    retryer.retry(policy) {
                        if (index > 0) {
                            assertEquals(
                                invocation[index - 1].expected.retryQuota,
                                tokenBucket.capacity,
                                "Quota mismatch after response ${index - 1} in '$name'",
                            )
                        }
                        val resp = invocation[index++]
                        currentCoroutineContext()[RetryContext]!!.retryAfter = resp.response.parseRetryAfter()
                        resp.response.toResult()
                    }
                }
            }

            assertEquals(
                tc.responses.last().expected.retryQuota,
                tokenBucket.capacity,
                "Final quota mismatch for '$name'",
            )
        }
    }

    /**
     * Shared multi-threaded scenarios.
     * Each thread list is launched concurrently; all share the same token bucket.
     * The exact sequence of thread execution and specific values may vary.
     * We verify only the final quota.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testMultiThreadedCases() = runTest {
        val testCases = newStandardRetryMultiThreadedTestCases.deserializeYaml(NewStandardMultiThreadedTestCase.serializer())
        testCases.forEach { (name, tc) ->
            val tokenBucket = sepTokenBucket(tc.given.initialRetryTokens)
            val retryer = buildStrategy(tc.given, tokenBucket)

            val serverSidePolicy = object : RetryPolicy<Ok> {
                override fun evaluate(result: Result<Ok>): RetryDirective = when {
                    result.isSuccess -> RetryDirective.TerminateAndSucceed
                    else -> RetryDirective.RetryError(RetryErrorType.ServerSide)
                }
            }

            coroutineScope {
                for (threadResponses in tc.threads) {
                    launch {
                        var index = 0
                        retryer.retry(serverSidePolicy) {
                            threadResponses[index++].response.toResult()
                        }
                    }
                }
            }

            assertEquals(tc.expectedFinalQuota, tokenBucket.capacity, "Final quota mismatch for '$name'")
        }
    }

    /**
     * Long-polling operations back off even when retry quota is exhausted.
     * Also verifies that long-polling does NOT add delays for max_attempts_exceeded, success, or non-retryable errors.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testLongPollingCases() = runTest {
        val testCases = newStandardRetryLongPollingTestCases.deserializeYaml(NewStandardRetryTestCase.serializer())
        testCases.forEach { (name, tc) ->
            val tokenBucket = sepTokenBucket(tc.given.initialRetryTokens)
            val retryer = buildStrategy(tc.given, tokenBucket)

            val policy = NewSepRetryPolicy(tc.responses)
            val retryCtx = RetryContext().apply { isLongPolling = tc.given.longPolling }
            val block = object {
                var index = 0
                suspend fun doIt(): Ok {
                    val resp = tc.responses[index++]
                    currentCoroutineContext()[RetryContext]!!.retryAfter = resp.response.parseRetryAfter()
                    return resp.response.toResult()
                }
            }::doIt

            val startTimeMs = currentTime
            val result = runCatching { withContext(retryCtx) { retryer.retry(policy, block) } }
            val totalDelayMs = currentTime - startTimeMs

            val finalState = tc.responses.last().expected
            when (finalState.outcome) {
                NewStandardTestOutcome.RetryQuotaExceeded,
                NewStandardTestOutcome.MaxAttemptsExceeded,
                NewStandardTestOutcome.FailRequest -> {
                    assertIs<HttpCodeException>(result.exceptionOrNull(), "Expected exception for '$name'")
                }
                NewStandardTestOutcome.Success -> {
                    assertTrue(result.isSuccess, "Expected success for '$name' but got ${result.exceptionOrNull()}")
                }
                else -> fail("Unexpected final outcome for '$name': ${finalState.outcome}")
            }

            finalState.retryQuota?.let {
                assertEquals(it, tokenBucket.capacity, "Final quota mismatch for '$name'")
            }

            val expectedDelayMs = tc.responses
                .mapNotNull { it.expected.delay }
                .sumOf { (it * 1000).toLong() }
            assertEquals(expectedDelayMs, totalDelayMs, "Delay mismatch for '$name'")
        }
    }
}

private class NewSepRetryPolicy(private val responses: List<NewStandardResponseAndExpectation>) : RetryPolicy<Ok> {
    private var attempt = 0

    override fun evaluate(result: Result<Ok>): RetryDirective {
        val response = responses[attempt++].response
        return when {
            result.isSuccess -> RetryDirective.TerminateAndSucceed
            response.errorCode == "Throttling" -> RetryDirective.RetryError(RetryErrorType.Throttling)
            response.statusCode in setOf(500, 502, 503, 504) -> RetryDirective.RetryError(RetryErrorType.ServerSide)
            else -> RetryDirective.TerminateAndFail
        }
    }
}

private fun NewStandardResponse.parseRetryAfter(): Duration? = headers?.get("x-amz-retry-after")?.toLongOrNull()?.takeIf { it >= 0 }?.milliseconds

private fun NewStandardResponse.toResult() = when (statusCode) {
    200 -> Ok
    else -> throw HttpCodeException(statusCode)
}
