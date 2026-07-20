/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

// 2 * okio segment length (8k)
public const val DEFAULT_BYTE_CHANNEL_MAX_BUFFER_SIZE: Int = 8192 * 2

/**
 * Channel for asynchronous reading and writing sequences of bytes. Conceptually a pipe
 * with a reader on one end, decoupled from a writer on the other.
 *
 * This is a buffered **single-reader single writer channel**.
 *
 * Read operations can be invoked concurrently with write operations, but multiple reads or multiple writes
 * cannot be invoked concurrently with themselves. Exceptions are [close] and [flush] which can be invoked
 * concurrently with other operations including between themselves at any time.
 */
public interface SdkByteChannel :
    SdkByteReadChannel,
    SdkByteWriteChannel {
    override fun close() {
        close(null)
    }
}

/**
 * Create a buffered channel for asynchronous reading and writing of bytes
 * @param autoFlush Flag indicating if the channel should auto flush after every write, see [SdkByteWriteChannel.autoFlush]
 * @param maxBufferSize The maximum number of bytes allowed to be buffered by this channel without causing writes to
 * suspend
 */
@InternalApi
public fun SdkByteChannel(
    autoFlush: Boolean = true,
    maxBufferSize: Int = DEFAULT_BYTE_CHANNEL_MAX_BUFFER_SIZE,
): SdkByteChannel = RealSdkByteChannel(autoFlush, maxBufferSize)

/**
 * Appends up to [byteCount] bytes from [source] to this channel **without suspending**, writing only as many bytes
 * as currently fit in the channel's buffer ([SdkByteWriteChannel.availableForWrite]). Returns the number of bytes
 * actually written (which may be less than [byteCount], including `0` if the buffer is full).
 *
 * This is intended for producers that enforce their own external backpressure (e.g. a native reader bounded by a
 * flow-control window no larger than the channel's buffer) and therefore do not need the suspending semantics of
 * [SdkByteWriteChannel.write]. It is a single-writer operation and must not be invoked concurrently with
 * [SdkByteWriteChannel.write] or itself.
 *
 * @throws IllegalStateException if invoked on a channel implementation that does not support non-suspending writes
 */
@InternalApi
public fun SdkByteChannel.tryWrite(source: SdkBuffer, byteCount: Long = source.size): Long {
    val real = this as? RealSdkByteChannel
        ?: error("tryWrite is only supported on the default SdkByteChannel implementation, got ${this::class}")
    return real.tryWrite(source, byteCount)
}

/**
 * Creates a channel for reading with the contents of the given byte array.
 *
 * NOTE: The returned channel will be closed.
 *
 * @param content the bytes to write to the channel
 * @param offset the offset into [content] to start reading from
 * @param length the number of bytes to read from content
 */
@InternalApi
public fun SdkByteReadChannel(
    content: ByteArray,
    offset: Int = 0,
    length: Int = content.size - offset,
): SdkByteReadChannel = RealSdkByteChannel(content, offset, length)
