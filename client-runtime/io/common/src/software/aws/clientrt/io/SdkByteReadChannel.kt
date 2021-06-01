/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.io

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

internal const val DEFAULT_BUFFER_SIZE: Int = 4096

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

    /**
     * Read up to [limit] bytes into a [ByteArray] suspending until [limit] is reached or the channel
     * is closed.
     *
     * NOTE: Be careful as this will potentially read the entire byte stream into memory (up to limit)
     *
     * Check [availableForRead] and/or [isClosedForRead] to see if there is additional data left
     */
    suspend fun readRemaining(limit: Int = Int.MAX_VALUE): ByteArray

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
     * Suspend until *some* data is available or channel is closed
     */
    suspend fun awaitContent()

    /**
     * Close channel with optional cause cancellation.
     * This is an idempotent operation — subsequent invocations of this function have no effect and return false
     */
    fun cancel(cause: Throwable?): Boolean
}

/**
 * Reads up to [limit] bytes from receiver channel and writes them to [dst] channel.
 *
 * Closes [dst] channel when copy completes if [close] is `true`.
 * NOTE: Always closes [dst] channel if fails to read or write with cause exception.
 *
 * @return a number of bytes copied
 */
public suspend fun SdkByteReadChannel.copyTo(
    dst: SdkByteWriteChannel,
    limit: Long = Long.MAX_VALUE,
    close: Boolean = true
): Long {
    require(this !== dst)
    if (limit == 0L) return 0L

    // delegate to ktor-io if possible which may have further optimizations based on impl
    val cnt = if (this is KtorReadChannel && dst is KtorWriteChannel) {
        chan.copyTo(dst.chan, limit)
    } else {
        copyToFallback(dst, limit)
    }

    if (close) dst.close()

    return cnt
}

internal suspend fun SdkByteReadChannel.copyToFallback(dst: SdkByteWriteChannel, limit: Long): Long {
    val flushDst = !dst.autoFlush
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    try {
        var copied = 0L

        while (true) {
            val remaining = limit - copied
            if (remaining == 0L) break

            val rc = readAvailable(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (rc == -1) break

            dst.writeFully(buffer, 0, rc)
            copied += rc

            if (flushDst && availableForRead == 0) {
                dst.flush()
            }
        }

        return copied
    } catch (t: Throwable) {
        dst.close(t)
        throw t
    }
}

/**
 * Reads a single byte from the channel and suspends until available
 */
public suspend fun SdkByteReadChannel.readByte(): Byte {
    if (this is KtorReadChannel) return chan.readByte()
    // TODO - we could pool these
    val out = ByteArray(1)
    readFully(out)
    return out[0]
}

@OptIn(ExperimentalIoApi::class)
public suspend fun SdkByteReadChannel.readAvailable(dest: SdkBuffer, limit: Int = dest.writeRemaining): Int {
    if (this is KtorReadChannel) {
        return chan.read { source, start, endExclusive ->
            val rc = (endExclusive - start).toInt()
            if (rc > 0) {
                dest.reserve(rc)
                source.copyTo(dest.memory, start.toInt(), rc, dest.writePosition)
                dest.commitWritten(rc)
            }
            return@read rc
        }
    }

    return readAvailableFallback(dest, limit)
}

internal suspend fun SdkByteReadChannel.readAvailableFallback(dest: SdkBuffer, limit: Int): Int {
    if (availableForRead == 0) awaitContent()
    val tmp = ByteArray(availableForRead)
    dest.writeFully(tmp)
    return tmp.size
}
