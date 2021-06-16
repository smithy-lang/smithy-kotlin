/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import aws.smithy.kotlin.runtime.util.InternalApi

private class SdkBufferState {
    var writeHead: Int = 0
    var readHead: Int = 0
}

@OptIn(ExperimentalIoApi::class)
internal expect fun Memory.Companion.ofByteArray(src: ByteArray, offset: Int = 0, length: Int = src.size - offset): Memory

/**
 * A buffer with read and write positions. Similar in spirit to `java.nio.ByteBuffer` but for use
 * in Kotlin Multiplatform.
 *
 * Unlike `ByteBuffer`, this buffer will implicitly grow as needed to fulfill write requests.
 * However, explicitly reserving the required space up-front before a series of writes will be
 * more efficient.
 *
 * **concurrent unsafe**: Do not read/write using the same [SdkBuffer] instance from different threads.
 */
@OptIn(ExperimentalIoApi::class)
@InternalApi
class SdkBuffer internal constructor(
    // we make use of ktor-io's `Memory` type which already implements most of the functionality in a platform
    // agnostic way. We just need to wrap some methods around it
    internal var memory: Memory,
    val isReadOnly: Boolean = false
) {
    constructor(initialCapacity: Int, readOnly: Boolean = false) : this(DefaultAllocator.alloc(initialCapacity), readOnly)

    // TODO - we could implement Appendable but we would need to deal with Char as UTF-16 character
    //  (e.g. convert code points to number of bytes and write the correct utf bytes 1..4)

    companion object {
        /**
         * Create an SdkBuffer backed by the given ByteArray.
         * This *DOES NOT* make the bytes of [src] available for reading, call [commitWritten] to
         * mark bytes available for read.
         */
        fun of(src: ByteArray, offset: Int = 0, length: Int = src.size - offset): SdkBuffer = SdkBuffer(Memory.ofByteArray(src, offset, length))
    }

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
     * Reserve capacity for at least [count] bytes to be written.
     *
     * More than [count] bytes may be allocated in order to avoid frequent re-allocations.
     */
    fun reserve(count: Int) {
        if (writeRemaining >= count) return

        val minP2 = ceilp2(count + writePosition)
        val currP2 = ceilp2(memory.size32 + writePosition + 1)
        val newSize = maxOf(minP2, currP2)
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
     * Mark [count] bytes written and advance the [writePosition] by the same amount
     */
    @InternalApi
    fun commitWritten(count: Int) {
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
 * Creates a new, read-only byte buffer that shares this buffer's content.
 * Any attempts to write to the buffer will fail with [ReadOnlyBufferException]
 */
fun SdkBuffer.asReadOnly(): SdkBuffer = if (isReadOnly) this else SdkBuffer(memory, isReadOnly = true)

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
    require(offset >= 0) { "Invalid write offset, must be positive" }
    require(offset + length <= src.size) { "Invalid write: offset + length should be less than the source size: $offset + $length < ${src.size}" }
    writeSized(length) { memory, writeStart ->
        memory.storeByteArray(writeStart, src, offset, length)
        length
    }
}

/**
 * Reads at most [length] bytes from this buffer into the [dst] buffer
 * @return the number of bytes read
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readFully(dst: SdkBuffer, length: Int = dst.writeRemaining): Int {
    require(length >= 0)
    val rc = minOf(readRemaining, length)
    if (rc == 0) return 0
    return read { memory, readStart, _ ->
        dst.reserve(rc)
        memory.copyTo(dst.memory, readStart, rc, dst.writePosition)
        dst.commitWritten(rc)
        rc
    }
}

/**
 * Reads at most [length] bytes from this buffer or `-1` if no bytes are available for read.
 * @return the number of bytes read or -1 if the buffer is empty
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readAvailable(dst: SdkBuffer, length: Int = dst.writeRemaining): Int {
    if (!canRead) return -1
    val rc = minOf(readRemaining, length)
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

    writeSized(length) { memory, writeStart ->
        src.memory.copyTo(memory, src.readPosition, length, writeStart)
        src.discard(length)
    }
}

/**
 * Write the bytes of [str] as UTF-8
 */
// TODO - remove in favor of implementing Appendable in such a way as to not allocate an entire new byte array
fun SdkBuffer.write(str: String) = writeFully(str.encodeToByteArray())

/**
 * Read the available (unread) contents as a UTF-8 string
 */
fun SdkBuffer.decodeToString() = bytes().decodeToString(0, readRemaining)

/**
 * Get the available (unread) contents as a ByteArray.
 *
 * NOTE: This may or may not create a new backing array and perform a copy depending on the platform
 * and current buffer status.
 */
expect fun SdkBuffer.bytes(): ByteArray

@OptIn(ExperimentalIoApi::class)
internal inline fun SdkBuffer.read(block: (memory: Memory, readStart: Int, endExclusive: Int) -> Int): Int {
    val rc = block(memory, readPosition, writePosition)
    discard(rc)
    return rc
}

@OptIn(ExperimentalIoApi::class)
internal inline fun SdkBuffer.write(block: (memory: Memory, writeStart: Int, endExclusive: Int) -> Int): Int {
    if (isReadOnly) throw ReadOnlyBufferException("attempt to write to readOnly buffer at index: $writePosition")

    val wc = block(memory, writePosition, capacity)
    commitWritten(wc)
    return wc
}

@OptIn(ExperimentalIoApi::class)
internal inline fun SdkBuffer.writeSized(count: Int, block: (memory: Memory, writeStart: Int) -> Int): Int {
    reserve(count)
    return write { memory, writeStart, _ ->
        block(memory, writeStart)
    }
}
