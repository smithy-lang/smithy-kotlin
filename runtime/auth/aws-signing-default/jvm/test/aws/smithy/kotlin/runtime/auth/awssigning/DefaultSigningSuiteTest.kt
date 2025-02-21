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
        val result = Canonicalizer.Default.canonicalRequest(request, config)
        result.requestString
    }

    override val signatureProvider: SigningStateProvider = { request, config ->
        val canonical = Canonicalizer.Default.canonicalRequest(request, config)
        val stringToSign = SignatureCalculator.SigV4.stringToSign(canonical.requestString, config)
        val signingKey = SignatureCalculator.SigV4.signingKey(config)
        SignatureCalculator.SigV4.calculate(signingKey, stringToSign)
    }

    override val stringToSignProvider: SigningStateProvider = { request, config ->
        val canonical = Canonicalizer.Default.canonicalRequest(request, config)
        SignatureCalculator.SigV4.stringToSign(canonical.requestString, config)
    }
}
