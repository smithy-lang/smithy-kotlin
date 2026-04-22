/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucket
import aws.smithy.kotlin.runtime.retries.getOrThrow
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Integration tests for the SEP 2.1 "new" standard retry behavior.
 * These tests explicitly configure the new retry constants (retryCost=14, timeoutRetryCost=5)
 * and use [StandardExponentialBackoffWithJitter] (initialDelay=50ms, scaleFactor=2.0).
 */
class NewStandardRetryIntegrationTest {
    /** SEP 2.1 retry cost for non-throttling errors. */
    private val sepRetryCost = 14

    /** SEP 2.1 retry cost for throttling errors. */
    private val sepThrottlingRetryCost = 5

    private fun sepTokenBucket(maxCapacity: Int = 500) = StandardRetryTokenBucket {
        this.maxCapacity = maxCapacity
        retryCost = sepRetryCost
        timeoutRetryCost = sepThrottlingRetryCost
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testIntegrationCases() = runTest {
        val testCases = newStandardRetryIntegrationTestCases.deserializeYaml(NewStandardRetryTestCase.serializer())
        testCases.forEach { (name, tc) ->
            assertEquals(
                1.0,
                tc.given.exponentialBase,
                "Test runner only supports exponential_base=1.0 (no jitter), got ${tc.given.exponentialBase} in '$name'",
            )

            val tokenBucket = sepTokenBucket(tc.given.initialRetryTokens)
            val retryer = StandardRetryStrategy {
                maxAttempts = tc.given.maxAttempts
                this.tokenBucket = tokenBucket
                tc.given.service?.let { serviceName = it }
                delayProvider {
                    jitter = 0.0
                    maxBackoff = (tc.given.maxBackoffTime * 1000).toLong().milliseconds
                }
            }

            val policy = NewSepRetryPolicy(tc.responses)
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
                    // Propagate x-amz-retry-after header to the strategy (mirrors RetryMiddleware behavior)
                    retryer.retryAfterMillis = resp.response.headers
                        ?.get("x-amz-retry-after")
                        ?.toLongOrNull()
                        ?.takeIf { it >= 0 }
                    return resp.response.toResult()
                }
            }::doIt

            val startTimeMs = currentTime
            val result = runCatching { retryer.retry(policy, block) }
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
     * SEP 2.1: "Retry quota recovery after successful responses"
     * Multi-invocation test: responses are split on `success` outcomes, each group is a separate retry() call.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testMultiInvocationCases() = runTest {
        val testCases = newStandardRetryMultiInvocationTestCases.deserializeYaml(NewStandardRetryTestCase.serializer())
        testCases.forEach { (name, tc) ->
            val tokenBucket = sepTokenBucket(tc.given.initialRetryTokens)
            val retryer = StandardRetryStrategy {
                maxAttempts = tc.given.maxAttempts
                this.tokenBucket = tokenBucket
                tc.given.service?.let { serviceName = it }
                delayProvider {
                    jitter = 0.0
                    maxBackoff = (tc.given.maxBackoffTime * 1000).toLong().milliseconds
                }
            }

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
                retryer.retry(policy) {
                    val resp = invocation[index++]
                    retryer.retryAfterMillis = resp.response.headers
                        ?.get("x-amz-retry-after")
                        ?.toLongOrNull()
                        ?.takeIf { it >= 0 }
                    resp.response.toResult()
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
     * SEP 2.1: "Shared multi-threaded scenarios"
     * Each thread list is launched concurrently; all share the same token bucket.
     * Per SEP: "The exact sequence of thread execution and specific values may vary."
     * We verify only the final quota.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testMultiThreadedCases() = runTest {
        val testCases = newStandardRetryMultiThreadedTestCases.deserializeYaml(NewStandardMultiThreadedTestCase.serializer())
        testCases.forEach { (name, tc) ->
            val tokenBucket = sepTokenBucket(tc.given.initialRetryTokens)
            val retryer = StandardRetryStrategy {
                maxAttempts = tc.given.maxAttempts
                this.tokenBucket = tokenBucket
                tc.given.service?.let { serviceName = it }
                delayProvider {
                    jitter = 0.0
                    maxBackoff = (tc.given.maxBackoffTime * 1000).toLong().milliseconds
                }
            }

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
}

private class NewSepRetryPolicy(private val responses: List<NewStandardResponseAndExpectation>) : RetryPolicy<Ok> {
    private var attempt = 0

    override fun evaluate(result: Result<Ok>): RetryDirective {
        val response = responses[attempt++].response
        return when {
            result.isSuccess -> RetryDirective.TerminateAndSucceed
            response.errorCode == "Throttling" -> RetryDirective.RetryError(RetryErrorType.Throttling)
            else -> RetryDirective.RetryError(RetryErrorType.ServerSide)
        }
    }
}

private fun NewStandardResponse.toResult() = when (statusCode) {
    200 -> Ok
    else -> throw HttpCodeException(statusCode)
}
