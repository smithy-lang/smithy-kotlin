/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.text.byteCountUtf8
import io.ktor.utils.io.*

internal const val DEFAULT_BUFFER_SIZE: Int = 4096

/**
 * Supplies an asynchronous stream of bytes. Use this interface to read data from wherever it’s located:
 * from the network, storage, or a buffer in memory. This is a **single-reader channel**.
 */
public expect interface SdkByteReadChannel : Closeable {
    /**
     * Returns number of bytes that can be read without suspension. Read operations do no suspend and
     * return immediately when this number is at least the number of bytes requested for read.
     */
    public val availableForRead: Int

    /**
     * Returns `true` if the channel is closed and no remaining bytes are available for read. It implies
     * that availableForRead is zero.
     */
    public val isClosedForRead: Boolean

    /**
     * Returns `true` if the channel is closed from the writer side. [availableForRead] may be > 0
     */
    public val isClosedForWrite: Boolean

    /**
     * Read up to [limit] bytes into a [ByteArray] suspending until [limit] is reached or the channel
     * is closed.
     *
     * NOTE: Be careful as this will potentially read the entire byte stream into memory (up to limit)
     *
     * Check [availableForRead] and/or [isClosedForRead] to see if there is additional data left
     */
    public suspend fun readRemaining(limit: Int = Int.MAX_VALUE): ByteArray

    /**
     * Reads all length bytes to [sink] buffer or fails if source has been closed. Suspends if not enough
     * bytes available.
     */
    public suspend fun readFully(sink: ByteArray, offset: Int = 0, length: Int = sink.size - offset)

    /**
     * Reads all available bytes to [sink] buffer and returns immediately or suspends if no bytes available
     */
    public suspend fun readAvailable(sink: ByteArray, offset: Int = 0, length: Int = sink.size - offset): Int

    /**
     * Suspend until *some* data is available or channel is closed
     */
    public suspend fun awaitContent()

    /**
     * Close channel with optional cause cancellation.
     * This is an idempotent operation — subsequent invocations of this function have no effect and return false
     */
    public fun cancel(cause: Throwable?): Boolean
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

/**
 * Reads all available bytes to [dest] buffer and returns immediately or suspends if no bytes available
 */
public suspend fun SdkByteReadChannel.readAvailable(dest: SdkByteBuffer, limit: Long = dest.writeRemaining.toLong()): Long {
    if (this is KtorReadChannel) {
        return chan.read { source, start, endExclusive ->
            val rc = minOf(endExclusive - start, limit)
            if (rc > 0) {
                dest.reserve(rc)
                source.copyTo(dest.memory, start, rc, dest.writePosition.toLong())
                dest.advance(rc.toULong())
            }
            return@read rc.toInt()
        }.toLong()
    }

    return readAvailableFallback(dest, limit)
}

internal suspend fun SdkByteReadChannel.readAvailableFallback(dest: SdkByteBuffer, limit: Long): Long {
    if (availableForRead == 0) awaitContent()
    // channel was closed while waiting and no further content was made available
    if (availableForRead == 0 && isClosedForRead) return -1
    val tmp = ByteArray(minOf(availableForRead.toLong(), limit, Int.MAX_VALUE.toLong()).toInt())
    readFully(tmp)
    dest.writeFully(tmp)
    return tmp.size.toLong()
}

/**
 * Reads a UTF-8 code point from the channel. Returns `null` if closed
 */
public suspend fun SdkByteReadChannel.readUtf8CodePoint(): Int? {
    awaitContent()
    if (availableForRead == 0 && isClosedForRead) return null

    val firstByte = readByte()
    val cnt = byteCountUtf8(firstByte)
    var code = when (cnt) {
        1 -> firstByte.toInt()
        2 -> firstByte.toInt() and 0x1f
        3 -> firstByte.toInt() and 0x0f
        4 -> firstByte.toInt() and 0x07
        else -> throw IllegalStateException("Invalid UTF-8 start sequence: $firstByte")
    }

    for (i in 1 until cnt) {
        awaitContent()
        if (availableForRead == 0 && isClosedForRead) throw IllegalStateException("unexpected EOF: expected ${cnt - i} bytes")
        val byte = readByte()
        val bint = byte.toInt()
        if (bint and 0xc0 != 0x80) throw IllegalStateException("invalid UTF-8 successor byte: $byte")

        code = (code shl 6) or (bint and 0x3f)
    }

    return code
}
