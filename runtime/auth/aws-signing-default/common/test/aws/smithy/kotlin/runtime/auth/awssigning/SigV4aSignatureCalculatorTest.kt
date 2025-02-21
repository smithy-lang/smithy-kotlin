/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.PlatformProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SIGV4A_RESOURCES_BASE = "common/test/resources/sigv4a"

/**
 * Tests which are defined in resources/sigv4a
 */
private val TESTS = listOf(
    "get-header-key-duplicate",
    "get-header-value-multiline",
    "get-header-value-order",
    "get-header-value-trim",
    "get-relative-normalized",
    "get-relative-relative-normalized",
    "get-relative-relative-unnormalized",
    "get-relative-unnormalized",
    "get-slash-dot-slash-normalized",
    "get-slash-dot-slash-unnormalized",
    "get-slash-normalized",
    "get-slash-pointless-dot-normalized",
    "get-slash-pointless-dot-unnormalized",
    "get-slash-unnormalized",
    "get-slashes-normalized",
    "get-slashes-unnormalized",
    "get-space-normalized",
    "get-space-unnormalized",
    "get-unreserved",
    "get-utf8",
    "get-vanilla",
    "get-vanilla-empty-query-key",
    "get-vanilla-query",
    "get-vanilla-query-order-encoded",
    "get-vanilla-query-order-key-case",
    "get-vanilla-query-unreserved",
    "get-vanilla-utf8-query",
    "get-vanilla-with-session-token",
    "post-header-key-case",
    "post-header-key-sort",
    "post-header-value-case",
    "post-sts-header-after",
    "post-sts-header-before",
    "post-vanilla",
    "post-vanilla-empty-query-value",
    "post-vanilla-query",
    "post-x-www-form-urlencoded",
    "post-x-www-form-urlencoded-parameters",
)

class SigV4aSignatureCalculatorTest {
    @Test
    fun testStringToSign() = TESTS.forEach { testId ->
        runTest {
            val testDir = "$SIGV4A_RESOURCES_BASE/$testId/"
            assertTrue(PlatformProvider.System.fileExists(testDir), "Failed to find test directory for $testId")

            val context = Json.parseToJsonElement(testDir.fileContents("context.json")).jsonObject
            val signingConfig = context.parseAwsSigningConfig()

            val expectedStringToSign = testDir.fileContents("header-string-to-sign.txt")
            val canonicalRequest = testDir.fileContents("header-canonical-request.txt")
            val actualStringToSign = SignatureCalculator.SigV4a.stringToSign(canonicalRequest, signingConfig)

            assertEquals(expectedStringToSign, actualStringToSign)
        }
    }

    private fun JsonObject.parseAwsSigningConfig(): AwsSigningConfig {
        fun JsonObject.getStringValue(key: String): String {
            val value = checkNotNull(get(key)) { "Failed to find key $key in JSON object $this" }
            return value.toString().replace("\"", "")
        }

        val contextCredentials = checkNotNull(get("credentials")?.jsonObject) { "credentials unexpectedly null" }

        val credentials = Credentials(
            contextCredentials.getStringValue("access_key_id"),
            contextCredentials.getStringValue("secret_access_key"),
        )

        val region = getStringValue("region")
        val service = getStringValue("service")
        val signingDate = Instant.fromIso8601(getStringValue("timestamp"))

        return AwsSigningConfig {
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC
            this.credentials = credentials
            this.region = region
            this.service = service
            this.signingDate = signingDate
        }
    }

    private suspend fun String.fileContents(path: String): String = checkNotNull(PlatformProvider.System.readFileOrNull(this + path)?.decodeToString()) {
        "Unable to read contents at ${this + path}"
    }
}
