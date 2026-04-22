/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Test cases sourced from SEP 2.1 "new-retries" Appendix A — Standard Mode.
val newStandardRetryIntegrationTestCases = mapOf(
    "Retry eventually succeeds" to // language=YAML
        """
            given:
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 0.05
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 472
                  delay: 0.1
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 486
        """.trimIndent(),

    "Fail due to max attempts reached" to // language=YAML
        """
            given:
              exponential_base: 1
            responses:
              - response:
                  status_code: 502
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 0.05
              - response:
                  status_code: 502
                expected:
                  outcome: retry_request
                  retry_quota: 472
                  delay: 0.1
              - response:
                  status_code: 502
                expected:
                  outcome: max_attempts_exceeded
                  retry_quota: 472
        """.trimIndent(),

    "Retry Quota reached after a single retry" to // language=YAML
        """
            given:
              initial_retry_tokens: 14
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 0
                  delay: 0.05
              - response:
                  status_code: 500
                expected:
                  outcome: retry_quota_exceeded
                  retry_quota: 0
        """.trimIndent(),

    "No retries at all if retry quota is 0" to // language=YAML
        """
            given:
              initial_retry_tokens: 0
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_quota_exceeded
                  retry_quota: 0
        """.trimIndent(),

    "Verifying exponential backoff timing" to //language=YAML
        """
            given:
              max_attempts: 5
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 0.05
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 472
                  delay: 0.1
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 458
                  delay: 0.2
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 444
                  delay: 0.4
              - response:
                  status_code: 500
                expected:
                  outcome: max_attempts_exceeded
                  retry_quota: 444
        """.trimIndent(),

    "Verify max backoff time" to // language=YAML
        """
            given:
              max_attempts: 5
              exponential_base: 1
              max_backoff_time: 0.2
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 0.05
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 472
                  delay: 0.1
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 458
                  delay: 0.2
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 444
                  delay: 0.2
              - response:
                  status_code: 500
                expected:
                  outcome: max_attempts_exceeded
                  retry_quota: 444
        """.trimIndent(),

    "Retry stops after retry quota exhaustion" to // language=YAML
        """
            given:
              max_attempts: 5
              initial_retry_tokens: 20
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 6
                  delay: 0.05
              - response:
                  status_code: 502
                expected:
                  outcome: retry_quota_exceeded
                  retry_quota: 6
        """.trimIndent(),

    "Throttling error token bucket drain and backoff duration" to // language=YAML
        """
            given:
              exponential_base: 1
            responses:
              - response:
                  status_code: 400
                  error_code: Throttling
                expected:
                  outcome: retry_request
                  retry_quota: 495
                  delay: 1.0
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 500
        """.trimIndent(),

    "DynamoDB base backoff and increased retries" to // language=YAML
        """
            given:
              service: dynamodb
              max_attempts: 4
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 0.025
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 472
                  delay: 0.05
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 458
                  delay: 0.1
              - response:
                  status_code: 500
                expected:
                  outcome: max_attempts_exceeded
                  retry_quota: 458
        """.trimIndent(),

    "Honor x-amz-retry-after header" to // language=YAML
        """
            given:
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                  headers:
                    x-amz-retry-after: "1500"
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 1.5
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 500
        """.trimIndent(),

    "x-amz-retry-after minimum is exponential backoff duration" to // language=YAML
        """
            given:
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                  headers:
                    x-amz-retry-after: "0"
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 0.05
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 500
        """.trimIndent(),

    "x-amz-retry-after maximum is 5+exponential backoff duration" to // language=YAML
        """
            given:
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                  headers:
                    x-amz-retry-after: "10000"
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 5.05
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 500
        """.trimIndent(),

    "Invalid x-amz-retry-after falls back to exponential backoff" to // language=YAML
        """
            given:
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                  headers:
                    x-amz-retry-after: "invalid"
                expected:
                  outcome: retry_request
                  retry_quota: 486
                  delay: 0.05
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 500
        """.trimIndent(),

    // NOTE: Long-polling backoff test case ("Long-Polling Backoff When Token Bucket Empty")
    // is not yet implemented — long-polling support is deferred.
)

val newStandardRetryMultiInvocationTestCases = mapOf(
    "Retry quota recovery after successful responses" to // language=YAML
        """
            given:
              max_attempts: 5
              initial_retry_tokens: 30
              exponential_base: 1
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 16
                  delay: 0.05
              - response:
                  status_code: 502
                expected:
                  outcome: retry_request
                  retry_quota: 2
                  delay: 0.1
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 16
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 2
                  delay: 0.05
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 16
        """.trimIndent(),
)

val newStandardRetryMultiThreadedTestCases = mapOf(
    "Shared multi-threaded scenarios" to // language=YAML
        """
            given:
              max_attempts: 5
              exponential_base: 1
            threads:
              - - response:
                    status_code: 500
                  expected:
                    outcome: retry_request
                    retry_quota: 486
                - response:
                    status_code: 500
                  expected:
                    outcome: retry_request
                    retry_quota: 472
                - response:
                    status_code: 200
                  expected:
                    outcome: success
                    retry_quota: 486
              - - response:
                    status_code: 500
                  expected:
                    outcome: retry_request
                    retry_quota: 458
                - response:
                    status_code: 200
                  expected:
                    outcome: success
                    retry_quota: 472
            expected_final_quota: 486
        """.trimIndent(),
)

@Serializable
data class NewStandardRetryTestCase(val given: NewStandardGiven, val responses: List<NewStandardResponseAndExpectation>)

@Serializable
data class NewStandardGiven(
    @SerialName("max_attempts") val maxAttempts: Int = 3,
    @SerialName("initial_retry_tokens") val initialRetryTokens: Int = 500,
    @SerialName("exponential_base") val exponentialBase: Double = 1.0,
    @SerialName("max_backoff_time") val maxBackoffTime: Double = 20.0,
    val service: String? = null,
)

@Serializable
data class NewStandardResponseAndExpectation(val response: NewStandardResponse, val expected: NewStandardExpectation)

@Serializable
data class NewStandardResponse(
    @SerialName("status_code") val statusCode: Int,
    @SerialName("error_code") val errorCode: String? = null,
    val headers: Map<String, String>? = null,
)

@Serializable
data class NewStandardExpectation(
    val outcome: NewStandardTestOutcome,
    @SerialName("retry_quota") val retryQuota: Int,
    val delay: Double? = null,
)

@Serializable
enum class NewStandardTestOutcome {
    @SerialName("max_attempts_exceeded")
    MaxAttemptsExceeded,

    @SerialName("retry_quota_exceeded")
    RetryQuotaExceeded,

    @SerialName("retry_request")
    RetryRequest,

    @SerialName("success")
    Success,
}

@Serializable
data class NewStandardMultiThreadedTestCase(
    val given: NewStandardGiven,
    val threads: List<List<NewStandardResponseAndExpectation>>,
    @SerialName("expected_final_quota") val expectedFinalQuota: Int,
)
