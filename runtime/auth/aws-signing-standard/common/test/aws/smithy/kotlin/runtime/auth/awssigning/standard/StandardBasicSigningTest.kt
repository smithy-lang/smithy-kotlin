/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning.standard

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.tests.BasicSigningTestBase
import kotlinx.coroutines.test.TestResult
import kotlin.test.Ignore
import kotlin.test.Test

class StandardBasicSigningTest : BasicSigningTestBase() {
    override val signer: AwsSigner = StandardAwsSigner

    @Ignore
    @Test
    override fun testSignRequestSigV4Asymmetric(): TestResult = TODO("Add support for SigV4a in standard signer")
}
