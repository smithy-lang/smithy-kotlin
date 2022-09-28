package aws.smithy.kotlin.runtime.http.testutils

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

expect class MockByteReadChannel(contents: String, isClosedForRead: Boolean = true, isClosedForWrite: Boolean = true) :
    SdkByteReadChannel
