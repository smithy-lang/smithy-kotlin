/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucket
import aws.smithy.kotlin.runtime.retries.getOrThrow
import aws.smithy.kotlin.runtime.retries.policy.RetryDirective
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class StandardRetryIntegrationTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testIntegrationCases() = runTest {
        val testCases = standardRetryIntegrationTestCases.deserializeYaml(StandardRetryTestCase.serializer())
        testCases.forEach { (name, tc) ->
            val tokenBucket = StandardRetryTokenBucket { maxCapacity = tc.given.initialRetryTokens }
            val retryer = StandardRetryStrategy {
                maxAttempts = tc.given.maxAttempts
                this.tokenBucket = tokenBucket
                delayProvider {
                    initialDelay = tc.given.exponentialBase.milliseconds
                    scaleFactor = tc.given.exponentialPower
                    jitter = 0.0 // None of the tests use jitter
                    maxBackoff = tc.given.maxBackoffTime.milliseconds
                }
            }

            val block = object {
                var index = 0
                suspend fun doIt() = tc.responses[index++].response.getOrThrow()
            }::doIt

            val startTimeMs = currentTime
            val result = runCatching { retryer.retry(IntegrationTestPolicy, block) }
            val totalDelayMs = currentTime - startTimeMs

            val finalState = tc.responses.last().expected
            when (finalState.outcome) {
                TestOutcome.Success ->
                    assertEquals(Ok, result.getOrThrow().getOrThrow(), "Unexpected outcome for $name")

                TestOutcome.MaxAttemptsExceeded -> {
                    val e = assertThrows<HttpCodeException>("Expected exception for $name") {
                        result.getOrThrow()
                    }

                    assertEquals(tc.responses.last().response.statusCode, e.code, "Unexpected error code for $name")
                }

                TestOutcome.RetryQuotaExceeded -> {
                    val e = assertThrows<HttpCodeException>("Expected exception for $name") {
                        result.getOrThrow()
                    }

                    assertEquals(tc.responses.last().response.statusCode, e.code, "Unexpected error code for $name")

                    assertTrue("Expected retry capacity message in exception for $name") {
                        "Insufficient client capacity to attempt retry" in e.message
                    }
                }

                else -> fail("Unexpected outcome for $name: ${finalState.outcome}")
            }

            val expectedDelayMs = tc.responses.mapNotNull { it.expected.delay }.sum()
            if (finalState.outcome == TestOutcome.RetryQuotaExceeded) {
                // The retry quota exceeded tests assume that the delayer won't be called when the bucket's out of
                // capacity but that assumes no refill which is not the case most of the time. Rather than add
                // specialized handling in the strategy, simplify verify that we saw *at least* as much delay as
                // expected, rather than exactly an amount that presumes some obscure optimization.
                assertTrue(
                    expectedDelayMs <= totalDelayMs.toInt(),
                    "Unexpected delay for $name. Expected at least $expectedDelayMs but was $totalDelayMs",
                )
            } else {
                assertEquals(expectedDelayMs, totalDelayMs.toInt(), "Unexpected delay for $name")
            }

            assertEquals(finalState.retryQuota, tokenBucket.capacity)
        }
    }
}

object IntegrationTestPolicy : RetryPolicy<Ok> {
    override fun evaluate(result: Result<Ok>): RetryDirective = when {
        result.isSuccess -> RetryDirective.TerminateAndSucceed
        result.isFailure -> RetryDirective.RetryError(RetryErrorType.ServerSide)
        else -> fail("Unexpected result condition")
    }
}

data object Ok

class HttpCodeException(val code: Int) : ServiceException()

fun Response.getOrThrow() = when (statusCode) {
    200 -> Ok
    else -> throw HttpCodeException(statusCode)
}
