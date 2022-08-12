/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.SigningStateProvider
import aws.smithy.kotlin.runtime.auth.awssigning.tests.SigningSuiteTestBase

class DefaultSigningSuiteTest : SigningSuiteTestBase() {
    override val signer: AwsSigner = DefaultAwsSigner

    override val canonicalRequestProvider: SigningStateProvider = { request, config ->
        val credentials = config.credentialsProvider.getCredentials()
        val result = Canonicalizer.Default.canonicalRequest(request, config, credentials)
        result.requestString
    }

    override val signatureProvider: SigningStateProvider = { request, config ->
        val credentials = config.credentialsProvider.getCredentials()
        val canonical = Canonicalizer.Default.canonicalRequest(request, config, credentials)
        val stringToSign = SignatureCalculator.Default.stringToSign(canonical.requestString, config)
        val signingKey = SignatureCalculator.Default.signingKey(config, credentials)
        SignatureCalculator.Default.calculate(signingKey, stringToSign)
    }

    override val stringToSignProvider: SigningStateProvider = { request, config ->
        val credentials = config.credentialsProvider.getCredentials()
        val canonical = Canonicalizer.Default.canonicalRequest(request, config, credentials)
        SignatureCalculator.Default.stringToSign(canonical.requestString, config)
    }
}
