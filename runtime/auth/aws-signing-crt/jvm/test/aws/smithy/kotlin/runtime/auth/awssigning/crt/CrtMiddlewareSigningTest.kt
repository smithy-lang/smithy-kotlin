/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning.crt

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.tests.MiddlewareSigningTestBase

class CrtMiddlewareSigningTest : MiddlewareSigningTestBase() {
    override val signer: AwsSigner = CrtAwsSigner
}
