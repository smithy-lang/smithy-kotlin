/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.io

/**
 * Supplies a stream of bytes. Use this interface to read data from wherever it’s located: from the network, storage, or a buffer in memory.
 *
 * This interface is functionally equivalent to an asynchronous coroutine compatible [java.io.InputStream]
 */
expect interface Source {
    /**
     * Returns number of bytes that can be read without suspension. Read operations do no suspend and return immediately when this number is at least the number of bytes requested for read.
     */
    val availableForRead: Int

    /**
     * Returns true if the channel is closed and no remaining bytes are available for read. It implies that availableForRead is zero.
     */
    val isClosedForRead: Boolean

    val isClosedForWrite: Boolean

    /**
     * Read the entire content into a [ByteArray]. NOTE: Be careful this will read the entire byte stream into memory.
     */
    suspend fun readAll(): ByteArray

    /**
     * Reads all length bytes to [sink] buffer or fails if source has been closed. Suspends if not enough bytes available.
     */
    suspend fun readFully(sink: ByteArray, offset: Int, length: Int)

    /**
     * Reads all available bytes to [sink] buffer and returns immediately or suspends if no bytes available
     */
    suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int

    /**
     * Close channel with optional cause cancellation
     */
    fun cancel(cause: Throwable?): Boolean
}
