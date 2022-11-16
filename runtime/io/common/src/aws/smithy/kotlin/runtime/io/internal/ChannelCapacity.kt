/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.internal

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

// implementation details of RealSdkByteChannel
internal class ChannelCapacity(private val totalCapacity: Int) {
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
