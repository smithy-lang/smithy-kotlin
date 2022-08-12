/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.BasicSigningTestBase
import kotlinx.coroutines.test.TestResult
import kotlin.test.Ignore
import kotlin.test.Test

class DefaultBasicSigningTest : BasicSigningTestBase() {
    override val signer: AwsSigner = DefaultAwsSigner

    @Ignore
    @Test
    override fun testSignRequestSigV4Asymmetric(): TestResult = TODO("Add support for SigV4a in default signer")
}
