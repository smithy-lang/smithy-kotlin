/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import java.nio.ByteBuffer
import io.ktor.utils.io.ByteReadChannel as KtorByteReadChannel

/**
 * Supplies a stream of bytes. Use this interface to read data from wherever it’s located: from the network, storage, or a buffer in memory.
 *
 * This interface is functionally equivalent to an asynchronous coroutine compatible [java.io.InputStream]
 */
actual interface SdkByteReadChannel : Closeable {
    /**
     * Returns number of bytes that can be read without suspension. Read operations do no suspend and return immediately when this number is at least the number of bytes requested for read.
     */
    actual val availableForRead: Int

    /**
     * Returns true if the channel is closed and no remaining bytes are available for read. It implies that availableForRead is zero.
     */
    actual val isClosedForRead: Boolean

    actual val isClosedForWrite: Boolean

    /**
     * Read up to [limit] bytes into a [ByteArray] suspending until [limit] is reached or the channel
     * is closed.
     *
     * NOTE: Be careful as this will potentially read the entire byte stream into memory (up to limit)
     */
    actual suspend fun readRemaining(limit: Int): ByteArray

    /**
     * Reads all length bytes to [sink] buffer or fails if source has been closed. Suspends if not enough bytes available.
     */
    actual suspend fun readFully(sink: ByteArray, offset: Int, length: Int)

    /**
     * Reads all available bytes to [sink] buffer and returns immediately or suspends if no bytes available
     * @return number of bytes read or `-1` if the channel has been closed
     */
    actual suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int
    suspend fun readAvailable(sink: ByteBuffer): Int

    actual suspend fun awaitContent()

    /**
     * Close channel with optional cause cancellation
     */
    actual fun cancel(cause: Throwable?): Boolean

    override fun close() { cancel(null) }
}

/**
 * Creates a channel for reading from the given buffer
 */
fun SdkByteReadChannel(content: ByteBuffer): SdkByteReadChannel = KtorByteReadChannel(content).toSdkChannel()
