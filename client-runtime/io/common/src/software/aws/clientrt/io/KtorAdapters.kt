/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.ByteChannel as KtorByteChannel
import io.ktor.utils.io.ByteReadChannel as KtorByteReadChannel
import io.ktor.utils.io.ByteWriteChannel as KtorByteWriteChannel

/**
 * Wrap ktor's ByteReadChannel as our own. This implements the common API of [ByteReadChannel]. Only
 * platform specific differences in interfaces need be implemented in inheritors.
 */
internal abstract class KtorReadChannelAdapterBase(
    val chan: KtorByteReadChannel
) : ByteReadChannel {

    override val availableForRead: Int
        get() = chan.availableForRead

    override val isClosedForRead: Boolean
        get() = chan.isClosedForRead

    override val isClosedForWrite: Boolean
        get() = chan.isClosedForWrite

    override suspend fun readAll(): ByteArray {
        return chan.readRemaining().readBytes()
    }

    override suspend fun readFully(sink: ByteArray, offset: Int, length: Int) {
        chan.readFully(sink, offset, length)
    }

    override suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int {
        return chan.readAvailable(sink, offset, length)
    }

    override fun cancel(cause: Throwable?): Boolean {
        return chan.cancel(cause)
    }
}

/**
 * Wrap ktor's ByteWriteChannel as our own. This implements the common API of [ByteWriteChannel]. Only
 * platform specific differences in interfaces need be implemented in inheritors.
 */
internal abstract class KtorWriteChannelAdapterBase(
    val chan: KtorByteWriteChannel
) : ByteWriteChannel {
    override val availableForWrite: Int
        get() = chan.availableForWrite

    override val isClosedForWrite: Boolean
        get() = chan.isClosedForWrite

    override val totalBytesWritten: Long
        get() = chan.totalBytesWritten

    override val autoFlush: Boolean
        get() = chan.autoFlush

    override suspend fun writeFully(src: ByteArray, offset: Int, length: Int) {
        chan.writeFully(src, offset, length)
    }

    override suspend fun writeAvailable(src: ByteArray, offset: Int, length: Int): Int {
        return chan.writeAvailable(src, offset, length)
    }

    override suspend fun close(cause: Throwable?): Boolean {
        return chan.close(cause)
    }

    override fun flush() {
        chan.flush()
    }
}

/**
 * Wrap ktor's ByteChannel as our own. This implements the common API of [ByteChannel]. Only
 * platform specific differences in interfaces need be implemented in inheritors.
 */

internal class KtorByteChannelAdapter(
    val chan: KtorByteChannel
) : ByteChannel,
    ByteReadChannel by KtorReadChannelAdapter(chan),
    ByteWriteChannel by KtorWriteChannelAdapter(chan) {
    override val isClosedForWrite: Boolean
        get() = chan.isClosedForWrite
}

internal expect class KtorReadChannelAdapter(chan: KtorByteReadChannel) : ByteReadChannel
internal expect class KtorWriteChannelAdapter(chan: KtorByteWriteChannel) : ByteWriteChannel

internal fun KtorByteReadChannel.toSdkChannel(): ByteReadChannel = KtorReadChannelAdapter(this)
internal fun KtorByteWriteChannel.toSdkChannel(): ByteWriteChannel = KtorWriteChannelAdapter(this)
internal fun KtorByteChannel.toSdkChannel(): ByteChannel = KtorByteChannelAdapter(this)
