/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

/**
 * A channel for writing a sequence of bytes asynchronously. This is a **single writer channel**.
 */
public interface SdkByteWriteChannel : Closeable {

    /**
     * Returns the number of bytes that can be written without suspension. Write operations do not
     * suspend and return immediately when this number is at least the number of bytes requested for
     * write.
     */
    public val availableForWrite: Int

    /**
     * Returns true if channel has been closed. Attempting to write to the channel will throw an exception
     */
    public val isClosedForWrite: Boolean

    /**
     * Returns the underlying cause the channel was closed with or `null` if closed successfully or not yet closed.
     * A failed channel will have a closed cause.
     */
    public val closedCause: Throwable?

    /**
     * Total number of bytes written to the channel.
     *
     * NOTE: not guaranteed to be atomic and may be updated in middle of a write operation
     */
    public val totalBytesWritten: Long

    /**
     * Returns `true` if the channel flushes automatically all pending bytes after every write operation.
     * If `false` then flush only happens when [flush] is explicitly called or when the buffer is full.
     */
    public val autoFlush: Boolean

    /**
     * Removes exactly [byteCount] bytes from [source] and appends them to this. Suspends until all bytes
     * have been written. **It is not safe to modify [source] until this function returns**
     * Throws [ClosedWriteChannelException] if this channel was already closed.
     *
     * @param source the buffer data will be read from and written to this channel
     * @param byteCount the number of bytes to read from source
     */
    public suspend fun write(source: SdkBuffer, byteCount: Long = source.size)

    /**
     * Attempts to append up to [byteCount] bytes from [source] to this channel **without suspending**, writing only
     * as many bytes as currently fit within [availableForWrite]. Returns the number of bytes actually written, which
     * may be less than [byteCount] (including `0` when the buffer is full).
     *
     * This is intended for producers that enforce their own external backpressure (for example a native reader
     * bounded by a flow-control window no larger than this channel's buffer) and therefore do not need the suspending
     * semantics of [write]. Like [write], this is a single-writer operation and must not be invoked concurrently with
     * [write] or itself; doing so may throw [IllegalStateException] ("Write operation already in progress").
     * Throws [ClosedWriteChannelException] if this channel was closed successfully, or the close cause if it was
     * closed with one.
     *
     * The default implementation throws [UnsupportedOperationException]; implementations that can service a
     * non-suspending write override it. Because it is a regular interface member, delegating wrappers
     * (e.g. `SdkByteChannel by delegate`) forward it to their delegate automatically.
     *
     * This is a low-level flow-control primitive intended for engine internals and is not part of the stable API.
     *
     * @param source the buffer data will be read from and written to this channel
     * @param byteCount the number of bytes to read from source
     * @return the number of bytes actually written
     */
    @InternalApi
    public fun tryWrite(source: SdkBuffer, byteCount: Long = source.size): Long = throw UnsupportedOperationException("${this::class.simpleName} does not support non-suspending writes")

    /**
     * Closes this channel with an optional exceptional [cause]. All pending bytes are flushed.
     * This is an idempotent operation — subsequent invocations of this function have no effect and return false
     *
     * @param cause the reason the channel is closed, a non-null cause will result in a "failed" channel whereas a
     * `null` cause will result in a closed channel (no more data will be written).
     * @return true if the channel was cancelled/closed by this invocation, false if the channel was already closed
     */
    public fun close(cause: Throwable?): Boolean

    /**
     * Flushes all pending write bytes making them available for read.
     * Thread safe and can be invoked at any time. It does nothing when invoked on a closed channel.
     */
    public fun flush(): Unit
}

/**
 * Convenience function to write as many bytes from [source] as possible without suspending. Returns
 * the number of bytes that could be written.
 *
 * @param source the buffer to read data from and write to this channel
 * @return the number of bytes that could be written
 */
public suspend fun SdkByteWriteChannel.writeAvailable(source: SdkBuffer): Long {
    val wc = minOf(availableForWrite.toLong(), source.size)
    write(source, wc)
    return wc
}

/**
 * Write [limit] bytes from [source] starting at [offset]. Suspends until all bytes can be written.
 */
public suspend fun SdkByteWriteChannel.write(source: ByteArray, offset: Int = 0, limit: Int = source.size - offset) {
    val buffer = SdkBuffer().apply { write(source, offset, limit) }
    write(buffer)
}
