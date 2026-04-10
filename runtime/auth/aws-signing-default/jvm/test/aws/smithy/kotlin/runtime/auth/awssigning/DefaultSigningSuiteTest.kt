/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.Sigv4TestSuiteTest
import aws.smithy.kotlin.runtime.auth.awssigning.tests.SigningSuiteTestBase
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.testing.parameterized
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSigningSuiteTest : SigningSuiteTestBase() {
    override val signer: AwsSigner = DefaultAwsSigner

    private suspend fun canonicalRequest(request: HttpRequest, config: AwsSigningConfig): String =
        Canonicalizer.Default.canonicalRequest(request, config).requestString

    private suspend fun signature(request: HttpRequest, config: AwsSigningConfig): String {
        val canonical = Canonicalizer.Default.canonicalRequest(request, config)
        val stringToSign = SignatureCalculator.SigV4.stringToSign(canonical.requestString, config)
        val signingKey = SignatureCalculator.SigV4.signingKey(config)
        return SignatureCalculator.SigV4.calculate(signingKey, stringToSign)
    }

    private suspend fun stringToSign(request: HttpRequest, config: AwsSigningConfig): String {
        val canonical = Canonicalizer.Default.canonicalRequest(request, config)
        return SignatureCalculator.SigV4.stringToSign(canonical.requestString, config)
    }

    @Test
    fun testCanonicalRequest() = parameterized(headerTestArgs() + queryTestArgs()) { test: Sigv4TestSuiteTest ->
        assertEquals(test.canonicalRequest, runBlocking { canonicalRequest(test.request.build(), test.config) })
    }

    @Test
    fun testSignature() = parameterized(headerTestArgs() + queryTestArgs()) { test: Sigv4TestSuiteTest ->
        assertEquals(test.signature, runBlocking { signature(test.request.build(), test.config) })
    }

    @Test
    fun testStringToSign() = parameterized(headerTestArgs() + queryTestArgs()) { test: Sigv4TestSuiteTest ->
        assertEquals(test.stringToSign, runBlocking { stringToSign(test.request.build(), test.config) })
    }
}
