/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

public actual interface SdkByteWriteChannel : Closeable {
    /**
     * Returns the number of bytes that can be written without suspension. Write operations do not
     * suspend and return immediately when this number is at least the number of bytes requested for
     * write.
     */
    public actual val availableForWrite: Int

    /**
     * Returns true if channel has been closed. Attempting to write to the channel will throw an exception
     */
    public actual val isClosedForWrite: Boolean

    /**
     * Total number of bytes written to the channel.
     *
     * NOTE: not guaranteed to be atomic and may be updated in middle of a write operation
     */
    public actual val totalBytesWritten: Long

    /**
     * Returns `true` if the channel flushes automatically all pending bytes after every write operation.
     * If `false` then flush only happens when [flush] is explicitly called or when the buffer is full.
     */
    public actual val autoFlush: Boolean

    /**
     * Writes all [src] bytes and suspends until all bytes written.
     */
    public actual suspend fun writeFully(src: ByteArray, offset: Int, length: Int)

    /**
     * Writes as much as possible and only suspends if buffer is full
     * Returns the byte count written.
     */
    public actual suspend fun writeAvailable(src: ByteArray, offset: Int, length: Int): Int

    /**
     * Closes this channel with an optional exceptional [cause]. All pending bytes are flushed.
     * This is an idempotent operation — subsequent invocations of this function have no effect and return false
     */
    public actual fun close(cause: Throwable?): Boolean

    /**
     * Flushes all pending write bytes making them available for read.
     * Thread safe and can be invoked at any time. It does nothing when invoked on a closed channel.
     */
    public actual fun flush()

    override fun close() { close(null) }
}
