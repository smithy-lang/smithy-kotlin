/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning.standard

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.tests.SigningSuiteTestBase

class StandardSigningSuiteTest : SigningSuiteTestBase() {
    override val signer: AwsSigner = StandardAwsSigner
}
