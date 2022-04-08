/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.signing.awssigning.crt

import aws.smithy.kotlin.runtime.auth.signing.awssigning.common.AwsSigner
import aws.smithy.kotlin.runtime.auth.signing.awssigning.tests.BasicSigningTestBase

class CrtBasicSigningTest : BasicSigningTestBase() {
    override val signer: AwsSigner = CrtAwsSigner
}
