package aws.smithy.kotlin.runtime.auth.awssigning.crt

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.tests.AwsChunkedByteReadChannelJVMTestBase
import aws.smithy.kotlin.runtime.auth.awssigning.tests.AwsChunkedByteReadChannelTestBase

class CrtAwsChunkedByteReadChannelTest : AwsChunkedByteReadChannelTestBase() {
    override val signer: AwsSigner = CrtAwsSigner
}

class CrtAwsChunkedByteReadChannelJVMTest : AwsChunkedByteReadChannelJVMTestBase() {
    override val signer: AwsSigner = CrtAwsSigner
}
