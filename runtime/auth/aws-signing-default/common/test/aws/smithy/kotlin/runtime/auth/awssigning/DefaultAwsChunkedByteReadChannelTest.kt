/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.AwsChunkedByteReadChannelTestBase

class DefaultAwsChunkedByteReadChannelTest : AwsChunkedByteReadChannelTestBase() {
    override val signer: AwsSigner = DefaultAwsSigner
}
