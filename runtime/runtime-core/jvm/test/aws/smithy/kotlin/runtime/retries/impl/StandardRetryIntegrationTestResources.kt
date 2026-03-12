/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.impl

// Test cases sourced from "new-retries" specification.
val standardRetryIntegrationTestCases = mapOf(
    "Retry eventually succeeds" to // language=YAML
        """
            given:
              max_attempts: 3
              initial_retry_tokens: 500
              exponential_base: 1
              exponential_power: 2
              max_backoff_time: 20
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 495
                  delay: 1
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 490
                  delay: 2
              - response:
                  status_code: 200
                expected:
                  outcome: success
                  retry_quota: 495
        """.trimIndent(),

    "Fail due to max attempts reached" to // language=YAML
        """
            given:
              max_attempts: 3
              initial_retry_tokens: 500
              exponential_base: 1
              exponential_power: 2
              max_backoff_time: 20
            responses:
              - response:
                  status_code: 502
                expected:
                  outcome: retry_request
                  retry_quota: 495
                  delay: 1
              - response:
                  status_code: 502
                expected:
                  outcome: retry_request
                  retry_quota: 490
                  delay: 2
              # Our third attempt is a failure, but we don't
              # retry anymore because we've reached max attempts of 3.
              - response:
                  status_code: 502
                expected:
                  outcome: max_attempts_exceeded
                  retry_quota: 490
        """.trimIndent(),

    "Retry Quota reached after a single retry" to // language=YAML
        """
            given:
              max_attempts: 3
              initial_retry_tokens: 5
              exponential_base: 1
              exponential_power: 2
              max_backoff_time: 20
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 0
                  delay: 1
              - response:
                  status_code: 502
                expected:
                  outcome: retry_quota_exceeded
                  retry_quota: 0
        """.trimIndent(),

    "No retries at all if retry quota is 0" to // language=YAML
        """
            given:
              max_attempts: 3
              initial_retry_tokens: 0
              exponential_base: 1
              exponential_power: 2
              max_backoff_time: 20
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_quota_exceeded
                  retry_quota: 0
        """.trimIndent(),

    "Verifying exponential backoff timing" to //language=YAML
        """
            # We need a higher max attempts than the default of
            # 3 to ensure we're doing this correctly.
            given:
              max_attempts: 5
              initial_retry_tokens: 500
              exponential_base: 1
              exponential_power: 2
              max_backoff_time: 20
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 495
                  delay: 1
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 490
                  delay: 2
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 485
                  delay: 4
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 480
                  delay: 8
              - response:
                  status_code: 500
                expected:
                  outcome: max_attempts_exceeded
                  retry_quota: 480
        """.trimIndent(),

    "Verify max backoff time" to // language=YAML
        """
            # We need a higher max attempts to ensure we're
            # doing this correctly.
            given:
              max_attempts: 5
              initial_retry_tokens: 500
              exponential_base: 1
              exponential_power: 2
              max_backoff_time: 3
            responses:
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 495
                  delay: 1
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 490
                  delay: 2
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 485
                  delay: 3
              - response:
                  status_code: 500
                expected:
                  outcome: retry_request
                  retry_quota: 480
                  delay: 3
              - response:
                  status_code: 500
                expected:
                  outcome: max_attempts_exceeded
                  retry_quota: 480
        """.trimIndent(),
)

data class StandardRetryTestCase(val given: Given, val responses: List<ResponseAndExpectation>)

data class Given(
    val maxAttempts: Int,
    val initialRetryTokens: Int,
    val exponentialBase: Double,
    val exponentialPower: Double,
    val maxBackoffTime: Int,
)

data class ResponseAndExpectation(val response: Response, val expected: Expectation)

data class Response(val statusCode: Int)

data class Expectation(
    val outcome: TestOutcome,
    val retryQuota: Int,
    val delay: Int? = null,
)

enum class TestOutcome {
    MAX_ATTEMPTS_EXCEEDED,
    RETRY_QUOTA_EXCEEDED,
    RETRY_REQUEST,
    SUCCESS,
}
