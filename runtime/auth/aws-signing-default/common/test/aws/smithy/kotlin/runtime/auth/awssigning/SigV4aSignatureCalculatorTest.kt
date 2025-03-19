/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.IgnoreNative
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

private const val SIGV4A_RESOURCES_BASE = "../aws-signing-tests/common/resources/aws-signing-test-suite/v4a"

/**
 * Tests which are defined in resources/sigv4a.
 * Copied directly from https://github.com/awslabs/aws-c-auth/tree/e8360a65e0f3337d4ac827945e00c3b55a641a5f/tests/aws-signing-test-suite/v4a.
 * get-vanilla-query-order-key and get-vanilla-query-order-value were deleted since they are not complete tests.
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

// TODO Add tests against header-signature.txt when java.security implements RFC 6979 / deterministic ECDSA. https://bugs.openjdk.org/browse/JDK-8239382
/**
 * Tests for [SigV4aSignatureCalculator]. Currently only tests forming the string-to-sign.
 */
class SigV4aSignatureCalculatorTest {
    @IgnoreNative // FIXME test resources are not loadable on iOS: https://youtrack.jetbrains.com/issue/KT-49981/
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

            assertEquals(expectedStringToSign, actualStringToSign, "$testId failed")
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

    private suspend fun String.fileContents(path: String): String = checkNotNull(
        PlatformProvider.System.readFileOrNull(this + path)
            ?.decodeToString()
            ?.replace("\r\n", "\n"),
    ) {
        "Unable to read contents at ${this + path}"
    }
}
