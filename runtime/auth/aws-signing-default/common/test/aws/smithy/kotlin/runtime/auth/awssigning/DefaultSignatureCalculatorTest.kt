/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.tests.testCredentialsProvider
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.decodeHexBytes
import aws.smithy.kotlin.runtime.util.encodeToHex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSignatureCalculatorTest {
    // Test adapted from https://docs.aws.amazon.com/general/latest/gr/sigv4-calculate-signature.html
    @Test
    fun testCalculate() {
        val signingKey = "c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9".decodeHexBytes()
        val stringToSign = """
            AWS4-HMAC-SHA256
            20150830T123600Z
            20150830/us-east-1/iam/aws4_request
            f536975d06c0309214f805bb90ccff089219ecd68b2577efef23edd43b7e1a59
        """.trimIndent()

        val expected = "5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7"
        val actual = SignatureCalculator.Default.calculate(signingKey, stringToSign)
        assertEquals(expected, actual)
    }

    // Test adapted from https://docs.aws.amazon.com/general/latest/gr/sigv4-calculate-signature.html
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testSigningKey() = runTest {
        val credentials = Credentials("", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")

        val config = AwsSigningConfig {
            signingDate = Instant.fromIso8601("20150830")
            region = "us-east-1"
            service = "iam"
            credentialsProvider = testCredentialsProvider
        }

        val expected = "c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9"
        val actual = SignatureCalculator.Default.signingKey(config, credentials).encodeToHex()
        assertEquals(expected, actual)
    }

    // Test adapted from https://docs.aws.amazon.com/general/latest/gr/sigv4-create-string-to-sign.html
    @Test
    fun testStringToSign() {
        val canonicalRequest = """
            GET
            /
            Action=ListUsers&Version=2010-05-08
            content-type:application/x-www-form-urlencoded; charset=utf-8
            host:iam.amazonaws.com
            x-amz-date:20150830T123600Z

            content-type;host;x-amz-date
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        """.trimIndent()

        val config = AwsSigningConfig {
            signingDate = Instant.fromIso8601("20150830T123600Z")
            region = "us-east-1"
            service = "iam"
            credentialsProvider = testCredentialsProvider
        }

        val expected = """
            AWS4-HMAC-SHA256
            20150830T123600Z
            20150830/us-east-1/iam/aws4_request
            f536975d06c0309214f805bb90ccff089219ecd68b2577efef23edd43b7e1a59
        """.trimIndent()
        val actual = SignatureCalculator.Default.stringToSign(canonicalRequest, config)
        assertEquals(expected, actual)
    }
}
