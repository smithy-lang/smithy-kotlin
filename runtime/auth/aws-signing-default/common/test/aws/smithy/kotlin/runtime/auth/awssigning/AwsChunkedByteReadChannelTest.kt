package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.AwsChunkedByteReadChannelTestBase

class AwsChunkedByteReadChannelTest : AwsChunkedByteReadChannelTestBase() {
    override val signer: AwsSigner = DefaultAwsSigner
}