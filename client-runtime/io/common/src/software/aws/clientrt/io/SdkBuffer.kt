/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import software.aws.clientrt.util.InternalApi

private class SdkBufferState {
    var writeHead: Int = 0
    var readHead: Int = 0
}

/**
 * A buffer with read and write positions
 *
 * **concurrent unsafe**: Do not read/write using the same [SdkBuffer] instance from different threads.
 */
@OptIn(ExperimentalIoApi::class)
@InternalApi
class SdkBuffer(
    capacity: Int
) {
    internal var memory = DefaultAllocator.alloc(capacity)
    private val state = SdkBufferState()

    /**
     * The total capacity of the buffer
     */
    val capacity: Int
        get() = memory.size32

    /**
     * The current read position. Always non-negative and <= [writePosition].
     */
    val readPosition: Int
        get() = state.readHead

    /**
     * The current write position. Will never run ahead of [capacity] and always greater than [readPosition]
     */
    val writePosition: Int
        get() = state.writeHead

    /**
     * Number of bytes available for reading
     */
    val readRemaining: Int
        get() = writePosition - readPosition

    /**
     * Free space left for writing
     */
    val writeRemaining: Int
        get() = capacity - writePosition

    /**
     * ensure there is enough write capacity for [minBytes]
     */
    private fun ensureWriteCapacity(minBytes: Int) {
        if (writeRemaining >= minBytes) return

        val newSize = (memory.size32 * 3 + 1) / 2
        memory = DefaultAllocator.realloc(memory, newSize)
    }

    /**
     * Discard [count] readable bytes
     */
    fun discard(count: Int): Int {
        require(count >= 0) { "cannot discard $count bytes; amount must be positive" }
        val size = minOf(count, readRemaining)
        state.readHead += size
        return size
    }

    /**
     * Rewind [readPosition] making [count] bytes available for reading again
     */
    fun rewind(count: Int = readPosition) {
        val size = minOf(count, readPosition)
        if (size <= 0) return

        state.readHead -= size
    }

    /**
     * Reset [readPosition] and [writePosition] making all bytes available for write and no bytes available to read
     */
    fun reset() {
        state.readHead = 0
        state.writeHead = 0
    }

    /**
     * mark [count] bytes written and advance the [writePosition] by the same amount
     */
    internal fun commitWritten(count: Int) {
        if (count <= 0) return
        require(count <= writeRemaining) { "Unable to write $count bytes; only $writeRemaining write capacity left" }
        state.writeHead += count
    }
}

/**
 * @return `true` if there are bytes to be read
 */
public inline val SdkBuffer.canRead: Boolean
    get() = writePosition > readPosition

/**
 * @return `true` if there is any free space to write
 */
public inline val SdkBuffer.canWrite: Boolean
    get() = writePosition < capacity

/**
 * Read from this buffer exactly [length] bytes and write to [dest] starting at [offset]
 * @throws IllegalArgumentException if there are not enough bytes available for read or the offset/length combination is invalid
 */
fun SdkBuffer.readFully(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset) {
    require(readRemaining >= length) { "Not enough bytes to read a ByteArray of size $length" }
    require(offset >= 0) { "Invalid read offset, must be positive: $offset" }
    require(offset + length <= dest.size) { "Invalid read: offset + length should be less than the destination size: $offset + $length < ${dest.size}" }
    read { memory, readStart, _ ->
        memory.loadByteArray(readStart, dest, offset, length)
        length
    }
}

/**
 * Read all available bytes from this buffer into [dest] starting at [offset] up to the destination buffers size.
 * If the total bytes available is less than [length] then as many bytes as are available will be read.
 * The total bytes read is returned or `-1` if no data is available.
 */
fun SdkBuffer.readAvailable(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset): Int {
    if (!canRead) return -1

    val rc = minOf(length, readRemaining)
    readFully(dest, offset, rc)
    return rc
}

/**
 * Write [length] bytes of [src] to this buffer starting at [offset]
 * @throws IllegalArgumentException if there is insufficient space or the offset/length combination is invalid
 */
fun SdkBuffer.writeFully(src: ByteArray, offset: Int = 0, length: Int = src.size - offset) {
    require(writeRemaining > length) { "Insufficient space to write $length bytes; capacity available: $writeRemaining" }
    require(offset >= 0) { "Invalid write offset, must be positive" }
    require(offset + length <= src.size) { "Invalid write: offset + length should be less than the source size: $offset + $length < ${src.size}" }
    write { memory, writeStart, _ ->
        memory.storeByteArray(writeStart, src, offset, length)
        length
    }
}

/**
 * Reads [length] bytes from this buffer into the [dst] buffer
 * @return the number of bytes read
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readFully(dst: SdkBuffer, length: Int = dst.writeRemaining): Int {
    require(length >= 0)
    require(length <= dst.writeRemaining)
    return read { memory, readStart, _ ->
        memory.copyTo(dst.memory, readStart, length, dst.writePosition)
        dst.commitWritten(length)
        length
    }
}

/**
 * Reads at most [length] bytes from this buffer or `-1` if no bytes are available for read.
 * @return the number of bytes read or -1 if the buffer is empty
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readAvailable(dst: SdkBuffer, length: Int = dst.writeRemaining): Int {
    if (!canRead) return -1
    val rc = minOf(dst.writeRemaining, readRemaining, length)
    return readFully(dst, rc)
}

/**
 * Write at most [length] bytes from [src] to this buffer
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.writeFully(src: SdkBuffer, length: Int = src.readRemaining) {
    require(length >= 0) { "length must be positive: $length" }
    require(length <= src.readRemaining) {
        "not enough bytes in source buffer to read $length bytes (${src.readRemaining} remaining)"
    }
    require(length <= writeRemaining) {
        "Insufficient space to write $length bytes; capacity available: $writeRemaining"
    }

    write { memory, writeStart, _ ->
        src.memory.copyTo(memory, src.readPosition, length, writeStart)
        src.discard(length)
    }
}

/**
 * Write the bytes of [str] as UTF-8
 */
fun SdkBuffer.write(str: String) = writeFully(str.encodeToByteArray())

/**
 * Read the available (unread) contents as a UTF-8 string
 */
fun SdkBuffer.decodeToString() = bytes().decodeToString()

/**
 * Get the available (unread) contents as a ByteArray.
 *
 * NOTE: This may or may not create a new backing array and perform a copy depending on the platform
 * and current buffer status.
 */
expect fun SdkBuffer.bytes(): ByteArray

@OptIn(ExperimentalIoApi::class)
private inline fun SdkBuffer.read(block: (memory: Memory, readStart: Int, endExclusive: Int) -> Int): Int {
    val rc = block(memory, readPosition, writePosition)
    discard(rc)
    return rc
}

@OptIn(ExperimentalIoApi::class)
private inline fun SdkBuffer.write(block: (memory: Memory, writeStart: Int, endExclusive: Int) -> Int): Int {
    val wc = block(memory, writePosition, capacity)
    commitWritten(wc)
    return wc
}

// FIXME - are we allowing this buffer to grow on it's own?
