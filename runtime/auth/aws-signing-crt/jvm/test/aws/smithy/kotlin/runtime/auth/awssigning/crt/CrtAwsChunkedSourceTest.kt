/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.crt

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.tests.AwsChunkedSourceTestBase
import aws.smithy.kotlin.runtime.auth.awssigning.crt.CrtAwsSigner

class CrtAwsChunkedSourceTest : AwsChunkedSourceTestBase() {
    override val signer: AwsSigner = CrtAwsSigner
}
