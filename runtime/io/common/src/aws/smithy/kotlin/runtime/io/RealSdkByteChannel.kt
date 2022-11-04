/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.AwaitingSlot
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField

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

    private val _state: AtomicRef<ChannelState> = atomic(ChannelState.IdleEmpty(maxBufferSize))

    private val state: ChannelState
        get() = _state.value

    private val _closed: AtomicRef<ClosedSentinel?> = atomic(null)

    private val closedCause: Throwable?
        get() = _closed.value?.cause

    override val availableForWrite: Int
        get() = state.capacity.availableForWrite

    override val availableForRead: Int
        get() = state.capacity.availableForRead

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
            // println("reader attempting to sleep: avr: $availableForRead; avw:$availableForWrite")
            slot.sleep { availableForRead < requested && !isClosedForRead }
        }
    }

    private suspend fun awaitBytesToWrite(requested: Int) {
        while (availableForWrite < requested && !isClosedForWrite) {
            // println("writer attempting flush before sleep: avr: $availableForRead; avw: $availableForWrite")
            if (!tryFlush()) {
                // println("writer attempting to sleep: avr: $availableForRead; avw: $availableForWrite")
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

        return rc
    }

    override suspend fun write(source: SdkBuffer, byteCount: Long) {
        ensureNotClosed()
        if (byteCount == 0L) return

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
        state.capacity.completeWrite(size)
        _totalBytesWritten.plusAssign(size.toLong())

        if (autoFlush || availableForWrite == 0) {
            flush()
        }
    }

    private fun afterRead(size: Int) {
        state.capacity.completeRead(size)
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
        if (state.capacity.pendingToFlush == 0) {
            slot.resume()
            return false
        }

        state.capacity.flush()
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

internal class BufferCapacity(private val totalCapacity: Int) {
    private val _availableForRead: AtomicInt = atomic(0)
    private val _availableForWrite: AtomicInt = atomic(totalCapacity)
    private val _pendingToFlush: AtomicInt = atomic(0)

    inline var availableForRead: Int
        get() = _availableForRead.value
        private set(value) {
            _availableForRead.value = value
        }

    inline var availableForWrite: Int
        get() = _availableForWrite.value
        private set(value) {
            _availableForWrite.value = value
        }

    inline var pendingToFlush: Int
        get() = _pendingToFlush.value
        set(value) {
            _pendingToFlush.value = value
        }

    inline val isEmpty: Boolean
        get() = _availableForWrite.value == totalCapacity

    inline val isFull: Boolean
        get() = _availableForWrite.value == 0

    override fun toString(): String =
        "BufferCapacity(availableForRead: $availableForRead, availableForWrite: $availableForWrite, " +
            "pendingFlush: $pendingToFlush, capacity: $totalCapacity)"

    /**
     * @return true if there are bytes available for read after flush
     */
    fun flush(): Boolean {
        val pending = _pendingToFlush.getAndSet(0)
        return if (pending == 0) {
            _availableForRead.value > 0
        } else {
            return _availableForRead.addAndGet(pending) > 0
        }
    }

    fun completeWrite(size: Int) {
        _availableForWrite.minusAssign(size)
        _pendingToFlush.plusAssign(size)
    }

    fun completeRead(size: Int) {
        _availableForRead.minusAssign(size)
        _availableForWrite.plusAssign(size)
    }
}

internal sealed class ChannelState(
    @JvmField val capacity: BufferCapacity,
) {
    class IdleEmpty(maxBufferSize: Int) : ChannelState(BufferCapacity(maxBufferSize))
}
