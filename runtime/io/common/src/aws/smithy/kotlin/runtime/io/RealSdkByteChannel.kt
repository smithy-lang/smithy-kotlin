/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.AwaitingSlot
import aws.smithy.kotlin.runtime.io.internal.ChannelCapacity
import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.coroutines.cancellation.CancellationException

internal data class ClosedSentinel(val cause: Throwable?)
private val CLOSED_SUCCESS = ClosedSentinel(null)

internal class RealSdkByteChannel(
    override val autoFlush: Boolean,
    maxBufferSize: Int,
) : SdkByteChannel {

    internal constructor(
        content: ByteArray,
        offset: Int = 0,
        length: Int = content.size - offset,
    ) : this(true, DEFAULT_BYTE_CHANNEL_MAX_BUFFER_SIZE) {
        require(length <= availableForWrite) { "Initial contents overflow maximum channel capacity" }
        buffer.write(content, offset, length)
        afterWrite(length)
    }

    private val lock = SynchronizedObject()
    private val buffer = SdkBuffer()

    private val capacity = ChannelCapacity(maxBufferSize)

    private val _readInProgress = atomic(false)
    private val _writeInProgress = atomic(false)

    private val _closed: AtomicRef<ClosedSentinel?> = atomic(null)

    override val closedCause: Throwable?
        get() = _closed.value?.cause

    override val availableForWrite: Int
        get() = capacity.availableForWrite

    override val availableForRead: Int
        get() = capacity.availableForRead

    override val isClosedForRead: Boolean
        get() = isClosedForWrite && availableForRead == 0

    override val isClosedForWrite: Boolean
        get() = _closed.value != null

    private val _totalBytesWritten = atomic(0L)

    override val totalBytesWritten: Long
        get() = _totalBytesWritten.value

    private val slot = AwaitingSlot()

    private suspend fun awaitBytesToRead(requested: Int) {
        while (availableForRead < requested && !isClosedForRead) {
            slot.sleep { availableForRead < requested && !isClosedForRead }
        }
    }

    private suspend fun awaitBytesToWrite(requested: Int) {
        while (availableForWrite < requested && !isClosedForWrite) {
            if (!tryFlush()) {
                slot.sleep { availableForWrite < requested && !isClosedForWrite }
            }
        }
    }

    private suspend fun awaitFreeSpace() {
        flush()
        awaitBytesToWrite(1)
        ensureNotClosed()
    }

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L) { "Read limit must be >= 0, was $limit" }
        ensureNotFailed()
        if (isClosedForRead) return -1L
        if (limit == 0L) return 0L

        return reading {
            if (availableForRead == 0) {
                awaitBytesToRead(1)
                ensureNotFailed()
                // closed while suspended
                if (isClosedForRead) return -1L
            }

            val rc = minOf(availableForRead.toLong(), limit)

            synchronized(lock) {
                sink.write(buffer, rc)
            }

            afterRead(rc.toInt())

            rc
        }
    }

    private inline fun reading(block: () -> Long): Long = try {
        check(_readInProgress.compareAndSet(false, true)) { "Read operation already in progress" }
        block()
    } finally {
        _readInProgress.compareAndSet(true, false)
    }

    override suspend fun write(source: SdkBuffer, byteCount: Long) {
        ensureNotClosed()
        if (byteCount == 0L) return

        writing {
            var remaining = byteCount

            while (remaining > 0) {
                if (availableForWrite == 0) {
                    awaitFreeSpace()
                }

                val wc = minOf(availableForWrite.toLong(), remaining)

                synchronized(lock) {
                    buffer.write(source, wc)
                }

                afterWrite(wc.toInt())
                remaining -= wc
            }
        }
    }

    private inline fun writing(block: () -> Unit) =
        try {
            check(_writeInProgress.compareAndSet(false, true)) { "Write operation already in progress" }
            block()
        } finally {
            _writeInProgress.compareAndSet(true, false)
        }

    // read side only
    private fun ensureNotFailed() {
        closedCause?.let { throw it }
    }

    // write side only
    private fun ensureNotClosed() {
        if (isClosedForWrite) {
            throw closedCause ?: ClosedWriteChannelException("Channel $this is already closed")
        }
    }

    private fun afterWrite(size: Int) {
        capacity.completeWrite(size)
        _totalBytesWritten.plusAssign(size.toLong())

        if (autoFlush || availableForWrite == 0) {
            flush()
        }
    }

    private fun afterRead(size: Int) {
        capacity.completeRead(size)
        slot.resume()
    }

    override fun cancel(cause: Throwable?): Boolean {
        if (isClosedForWrite) return false
        return close(cause ?: CancellationException("Channel cancelled"))
    }

    override fun flush() {
        tryFlush()
    }

    // try to flush pending bytes and advertise them to reader
    // returns true if pending bytes were flushed
    private fun tryFlush(): Boolean {
        if (capacity.pendingToFlush == 0) {
            slot.resume()
            return false
        }

        capacity.flush()
        slot.resume()
        return true
    }

    override fun close(cause: Throwable?): Boolean {
        if (isClosedForWrite) return false

        val updated = if (cause == null) CLOSED_SUCCESS else ClosedSentinel(cause)
        if (!_closed.compareAndSet(null, updated)) return false

        if (cause == null) {
            flush()
        } else {
            slot.cancel(cause)
        }

        return true
    }
}
