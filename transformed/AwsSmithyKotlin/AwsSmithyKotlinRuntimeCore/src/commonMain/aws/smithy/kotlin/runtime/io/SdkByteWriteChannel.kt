/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

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
