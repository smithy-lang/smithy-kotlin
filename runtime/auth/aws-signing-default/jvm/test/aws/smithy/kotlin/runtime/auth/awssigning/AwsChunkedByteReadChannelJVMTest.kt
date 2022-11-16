package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.AwsChunkedByteReadChannelJVMTestBase

class AwsChunkedByteReadChannelJVMTest : AwsChunkedByteReadChannelJVMTestBase() {
    override val signer: AwsSigner = DefaultAwsSigner
}