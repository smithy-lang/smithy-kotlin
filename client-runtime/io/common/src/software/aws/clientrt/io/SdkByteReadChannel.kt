/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.io

import io.ktor.utils.io.*

/**
 * Supplies an asynchronous stream of bytes. Use this interface to read data from wherever it’s located:
 * from the network, storage, or a buffer in memory. This is a **single-reader channel**.
 */
expect interface SdkByteReadChannel : Closeable {
    /**
     * Returns number of bytes that can be read without suspension. Read operations do no suspend and
     * return immediately when this number is at least the number of bytes requested for read.
     */
    val availableForRead: Int

    /**
     * Returns `true` if the channel is closed and no remaining bytes are available for read. It implies
     * that availableForRead is zero.
     */
    val isClosedForRead: Boolean

    /**
     * Returns `true` if the channel is closed from the writer side. [availableForRead] may be > 0
     */
    val isClosedForWrite: Boolean

    // FIXME - replace with readRemaining(limit: Long): ByteArray
    //  this blocks until EOF which means you can only invoke this on a closed channel currently.
    //  Without a limit it will _always_ block when channel isn't closed

    /**
     * Read the entire content into a [ByteArray]. NOTE: Be careful this will read the entire byte stream
     * into memory.
     */
    suspend fun readAll(): ByteArray

    /**
     * Reads all length bytes to [sink] buffer or fails if source has been closed. Suspends if not enough
     * bytes available.
     */
    suspend fun readFully(sink: ByteArray, offset: Int = 0, length: Int = sink.size - offset)

    /**
     * Reads all available bytes to [sink] buffer and returns immediately or suspends if no bytes available
     */
    suspend fun readAvailable(sink: ByteArray, offset: Int = 0, length: Int = sink.size - offset): Int

    /**
     * Close channel with optional cause cancellation.
     * This is an idempotent operation — subsequent invocations of this function have no effect and return false
     */
    fun cancel(cause: Throwable?): Boolean
}

/**
 * Reads up to [limit] bytes from receiver channel and writes them to [dst] channel.
 * Closes [dst] channel if fails to read or write with cause exception.
 * @return a number of bytes copied
 */
public suspend fun SdkByteReadChannel.copyTo(dst: SdkByteWriteChannel, limit: Long = Long.MAX_VALUE): Long {
    require(this !== dst)
    if (limit == 0L) return 0L

    // delegate to ktor-io if possible which may have further optimizations based on impl
    if (this is IsKtorReadChannel && dst is IsKtorWriteChannel) {
        return chan.copyTo(dst.chan)
    }

    TODO("not implemented")
}

private suspend fun SdkByteReadChannel.copyToImpl(dst: SdkByteWriteChannel, limit: Long): Long {
    try {
    } catch (t: Throwable) {
        dst.close(t)
        throw t
    } finally {
    }
    TODO("not implemented")
}

/**
 * Reads a single byte from the channel and suspends until available
 */
public suspend fun SdkByteReadChannel.readByte(): Byte {
    if (this is IsKtorReadChannel) return chan.readByte()
    // TODO - we could pool these
    val out = ByteArray(1)
    readFully(out)
    return out[0]
}
