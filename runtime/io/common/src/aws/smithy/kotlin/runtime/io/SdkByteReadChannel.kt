/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import java.io.EOFException

// FIXME - document read() and readFully() w.r.t a failed channel (does it throw, return -1, etc)

/**
 * Supplies an asynchronous stream of bytes. This is a **single-reader channel**.
 */
public interface SdkByteReadChannel : Closeable {
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
     * Remove at least 1 byte, and up-to [limit] bytes from this and appends them to [sink].
     * Suspends if no bytes are available. Returns the number of bytes read, or -1 if this
     * channel is exhausted. **It is not safe to modify [sink] until this function returns**
     */
    public suspend fun read(sink: SdkBuffer, limit: Long): Long

    /**
     * Close channel with optional cause cancellation.
     * This is an idempotent operation — subsequent invocations of this function have no effect and return false
     */
    public fun cancel(cause: Throwable?): Boolean
}

/**
 * Read exactly [byteCount] bytes from this into [sink] or throws [EOFException] if the channel is exhausted before
 * all bytes could be read.
 */
public suspend fun SdkByteReadChannel.readFully(sink: SdkBuffer, byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0L) {
        val rc = read(sink, remaining)
        if (rc == -1L) throw EOFException("Unexpected EOF: expected $remaining bytes; consumed: ${byteCount - remaining}")
        remaining -= rc
    }
}

/**
 * **Caution** Read the entire contents of the channel into [sink].
 * This function will suspend until the channel is exhausted and no bytes remain OR the channel cancelled
 */
public suspend fun SdkByteReadChannel.readRemaining(sink: SdkBuffer) {
    while (!isClosedForRead) {
        read(sink, Long.MAX_VALUE)
    }
}

/**
 * **Caution** Read the entire contents of the channel into a new buffer and return it.
 */
public suspend fun SdkByteReadChannel.readToBuffer(): SdkBuffer {
    val buffer = SdkBuffer()
    readRemaining(buffer)
    return buffer
}
