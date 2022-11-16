package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.AwsChunkedByteReadChannelTestBase

class DefaultAwsChunkedByteReadChannelTest : AwsChunkedByteReadChannelTestBase() {
    override val signer: AwsSigner = DefaultAwsSigner
}