/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.testutils

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import java.nio.ByteBuffer

actual class MockByteReadChannel actual constructor(
    val contents: String,
    override val isClosedForRead: Boolean,
    override val isClosedForWrite: Boolean,
) : SdkByteReadChannel {
    override val availableForRead: Int = 0
    override suspend fun awaitContent() = error("Not implemented")
    override fun cancel(cause: Throwable?) = error("Not implemented")
    override fun close() = error("Not implemented")
    override suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int) = error("Not implemented")
    override suspend fun readAvailable(sink: ByteBuffer): Int = error("Not implemented")
    override suspend fun readFully(sink: ByteArray, offset: Int, length: Int) = error("Not implemented")
    override suspend fun readRemaining(limit: Int) = contents.encodeToByteArray()
}
