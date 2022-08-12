/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning.crt

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.tests.SigningSuiteTestBase

class CrtSigningSuiteTest : SigningSuiteTestBase() {
    override val signer: AwsSigner = CrtAwsSigner

    override val disabledTests = super.disabledTests + setOf(
        // FIXME - Signature mismatch possibly related to https://github.com/awslabs/aws-crt-java/pull/419. Needs
        // investigation.
        "get-utf8",
    )
}
