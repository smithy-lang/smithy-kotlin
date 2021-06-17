/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io
import java.nio.ByteBuffer
import io.ktor.utils.io.ByteReadChannel as KtorByteReadChannel
import io.ktor.utils.io.ByteWriteChannel as KtorByteWriteChannel

internal actual class KtorReadChannelAdapter actual constructor(
    chan: KtorByteReadChannel
) : SdkByteReadChannel, KtorReadChannelAdapterBase(chan) {
    override suspend fun readAvailable(sink: ByteBuffer): Int = chan.readAvailable(sink)

    override suspend fun awaitContent() = chan.awaitContent()
}

internal actual class KtorWriteChannelAdapter actual constructor(
    chan: KtorByteWriteChannel
) : SdkByteWriteChannel, KtorWriteChannelAdapterBase(chan)
