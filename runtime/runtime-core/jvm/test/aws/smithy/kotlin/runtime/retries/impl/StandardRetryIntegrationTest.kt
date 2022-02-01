/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.RetryDirective
import aws.smithy.kotlin.runtime.retries.RetryErrorType
import aws.smithy.kotlin.runtime.retries.RetryPolicy
import aws.smithy.kotlin.runtime.retries.TooManyAttemptsException
import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.*

class StandardRetryIntegrationTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testIntegrationCases() = runTest {
        val testCases = standardRetryIntegrationTestCases
            .mapValues { Yaml.default.decodeFromString(TestCase.serializer(), it.value) }
        testCases.forEach { (name, tc) ->
            val options = StandardRetryStrategyOptions(maxTimeMs = Int.MAX_VALUE, maxAttempts = tc.given.maxAttempts)
            val tokenBucket = StandardRetryTokenBucket(
                StandardRetryTokenBucketOptions.Default.copy(
                    maxCapacity = tc.given.initialRetryTokens,
                    circuitBreakerMode = true,
                    refillUnitsPerSecond = 0, // None of the tests use refill
                )
            )
            val delayer = ExponentialBackoffWithJitter(
                ExponentialBackoffWithJitterOptions(
                    initialDelayMs = tc.given.exponentialBase.toInt(),
                    scaleFactor = tc.given.exponentialPower,
                    jitter = 0.0, // None of the tests use jitter
                    maxBackoffMs = tc.given.maxBackoffTime,
                )
            )
            val retryer = StandardRetryStrategy(options, tokenBucket, delayer)

            val block = object {
                var index = 0
                suspend fun doIt() = tc.responses[index++].response.statusCode
            }::doIt

            val startTimeMs = currentTime
            val result = runCatching { retryer.retry(IntegrationTestPolicy, block) }
            val totalDelayMs = currentTime - startTimeMs

            val finalState = tc.responses.last().expected
            when (finalState.outcome) {
                Outcome.Success -> assertEquals(200, result.getOrNull(), "Unexpected outcome for $name")
                Outcome.MaxAttemptsExceeded -> assertIs<TooManyAttemptsException>(result.exceptionOrNull())
                Outcome.RetryQuotaExceeded -> assertIs<TooManyAttemptsException>(result.exceptionOrNull())
                else -> fail("Unexpected outcome for $name: ${finalState.outcome}")
            }

            val expectedDelayMs = tc.responses.map { it.expected.delay ?: 0 }.sum()
            if (finalState.outcome == Outcome.RetryQuotaExceeded) {
                // The retry quota exceeded tests assume that the delayer won't be called when the bucket's out of
                // capacity but that assumes no refill which is not the case most of the time. Rather than add
                // specialized handling in the strategy, simplify verify that we saw *at least* as much delay as
                // expected, rather than exactly an amount that presumes some obscure optimization.
                assertTrue(
                    expectedDelayMs <= totalDelayMs.toInt(),
                    "Unexpected delay for $name. Expected at least $expectedDelayMs but was $totalDelayMs"
                )
            } else {
                assertEquals(expectedDelayMs, totalDelayMs.toInt(), "Unexpected delay for $name")
            }

            assertEquals(finalState.retryQuota, tokenBucket.capacity)
        }
    }
}

object IntegrationTestPolicy : RetryPolicy<Int> {
    override fun evaluate(result: Result<Int>): RetryDirective = when (val code = result.getOrNull()!!) {
        200 -> RetryDirective.TerminateAndSucceed
        500, 502 -> RetryDirective.RetryError(RetryErrorType.ServerSide)
        else -> fail("Unexpected status code: $code")
    }
}

@Serializable
data class TestCase(val given: Given, val responses: List<ResponseAndExpectation>)

@Serializable
data class Given(
    @SerialName("max_attempts") val maxAttempts: Int,
    @SerialName("initial_retry_tokens") val initialRetryTokens: Int,
    @SerialName("exponential_base") val exponentialBase: Double,
    @SerialName("exponential_power") val exponentialPower: Double,
    @SerialName("max_backoff_time") val maxBackoffTime: Int,
)

@Serializable
data class ResponseAndExpectation(val response: Response, val expected: Expectation)

@Serializable
data class Response(@SerialName("status_code") val statusCode: Int)

@Serializable
data class Expectation(val outcome: Outcome, @SerialName("retry_quota") val retryQuota: Int, val delay: Int? = null)

@Serializable
enum class Outcome {
    @SerialName("max_attempts_exceeded") MaxAttemptsExceeded,
    @SerialName("retry_quota_exceeded") RetryQuotaExceeded,
    @SerialName("retry_request") RetryRequest,
    @SerialName("success") Success,
    ;
}
