/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Test cases sourced from "new-retries" specification.
val adaptiveRetryCubicTestCases = mapOf(
    "Cubic success #1" to // language=YAML
        """
            given:
              last_max_rate: 10
              last_throttle_time: 5
            cases:
              - response: success
                timestamp: 5
                calculated_rate: 7.0
              - response: success
                timestamp: 6
                calculated_rate: 9.64893600966
              - response: success
                timestamp: 7
                calculated_rate: 10.000030849917364
              - response: success
                timestamp: 8
                calculated_rate: 10.453284520772092
              - response: success
                timestamp: 9
                calculated_rate: 13.408697022224185
              - response: success
                timestamp: 10
                calculated_rate: 21.26626835427364
              - response: success
                timestamp: 11
                calculated_rate: 36.425998516920465
        """.trimIndent(),

    "Cubic success #2" to // language=YAML
        """
            given:
              last_max_rate: 10
              last_throttle_time: 5
            cases:
              - response: success
                timestamp: 5
                calculated_rate: 7
              - response: success
                timestamp: 6
                calculated_rate: 9.64893600966
              - response: throttle
                timestamp: 7
                calculated_rate: 6.754255206761999
              - response: throttle
                timestamp: 8
                calculated_rate: 4.727978644733399
              - response: success
                timestamp: 9
                calculated_rate: 6.606547753887045
              - response: success
                timestamp: 10
                calculated_rate: 6.763279816944947
              - response: success
                timestamp: 11
                calculated_rate: 7.598174833907107
              - response: success
                timestamp: 12
                calculated_rate: 11.511232804773524
        """.trimIndent(),
)

// Test cases sourced from "new-retries" specification.
val adaptiveRetryE2eTestCases = mapOf(
    "End-to-end test for client sending rates" to // language=YAML
        """
            cases:
              - response: success
                timestamp: 0.2
                measured_tx_rate: 0.000000
                new_token_bucket_rate: 0.500000
              - response: success
                timestamp: 0.4
                measured_tx_rate: 0.000000
                new_token_bucket_rate: 0.500000
              - response: success
                timestamp: 0.6
                measured_tx_rate: 4.800000
                new_token_bucket_rate: 0.500000
              - response: success
                timestamp: 0.8
                measured_tx_rate: 4.800000
                new_token_bucket_rate: 0.500000
              - response: success
                timestamp: 1.0
                measured_tx_rate: 4.160000
                new_token_bucket_rate: 0.500000
              - response: success
                timestamp: 1.2
                measured_tx_rate: 4.160000
                new_token_bucket_rate: 0.691200
              - response: success
                timestamp: 1.4
                measured_tx_rate: 4.160000
                new_token_bucket_rate: 1.097600
              - response: success
                timestamp: 1.6
                measured_tx_rate: 5.632000
                new_token_bucket_rate: 1.638400
              - response: success
                timestamp: 1.8
                measured_tx_rate: 5.632000
                new_token_bucket_rate: 2.332800
              - response: throttle
                timestamp: 2.0
                measured_tx_rate: 4.326400
                new_token_bucket_rate: 3.028480
              - response: success
                timestamp: 2.2
                measured_tx_rate: 4.326400
                new_token_bucket_rate: 3.486639
              - response: success
                timestamp: 2.4
                measured_tx_rate: 4.326400
                new_token_bucket_rate: 3.821874
              - response: success
                timestamp: 2.6
                measured_tx_rate: 5.665280
                new_token_bucket_rate: 4.053386
              - response: success
                timestamp: 2.8
                measured_tx_rate: 5.665280
                new_token_bucket_rate: 4.200373
              - response: success
                timestamp: 3.0
                measured_tx_rate: 4.333056
                new_token_bucket_rate: 4.282037
              - response: throttle
                timestamp: 3.2
                measured_tx_rate: 4.333056
                new_token_bucket_rate: 2.997426
              - response: success
                timestamp: 3.4
                measured_tx_rate: 4.333056
                new_token_bucket_rate: 3.452226
        """.trimIndent(),
)

/* ktlint-disable annotation spacing-between-declarations-with-annotations */

@Serializable
enum class ResponseType(val errorType: RetryErrorType?) {
    @SerialName("success") Success(null),
    @SerialName("throttle") Throttle(RetryErrorType.Throttling),
}

@Serializable
data class CubicTestCase(val given: Given, val cases: List<Case>) {
    @Serializable
    data class Given(
        @SerialName("last_max_rate") val lastMaxRate: Double,
        @SerialName("last_throttle_time") val lastThrottleTimeSeconds: Double,
    )

    @Serializable
    data class Case(
        @SerialName("response") val response: ResponseType,
        @SerialName("timestamp") val tsSeconds: Double,
        @SerialName("calculated_rate") val calculatedRate: Double,
    )
}

@Serializable
data class E2eTestCase(val cases: List<Case>) {
    @Serializable
    data class Case(
        @SerialName("response") val response: ResponseType,
        @SerialName("timestamp") val tsSeconds: Double,
        @SerialName("measured_tx_rate") val measuredTxRate: Double,
        @SerialName("new_token_bucket_rate") val newTokenBucketRate: Double,
    )
}

/* ktlint-enable annotation spacing-between-declarations-with-annotations */
