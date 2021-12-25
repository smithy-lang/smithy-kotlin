/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.InternalApi
import io.ktor.utils.io.bits.*

private class SdkBufferState {
    var writeHead: ULong = 0u
    var readHead: ULong = 0u
}

internal expect fun Memory.Companion.ofByteArray(src: ByteArray, offset: Int = 0, length: Int = src.size - offset): Memory

/**
 * A buffer with read and write positions. Similar in spirit to `java.nio.ByteBuffer` but for use
 * in Kotlin Multiplatform.
 *
 * Unlike `ByteBuffer`, this buffer will implicitly grow as needed to fulfill write requests.
 * However, explicitly reserving the required space up-front before a series of writes will be
 * more efficient.
 *
 * **concurrent unsafe**: Do not read/write using the same [SdkByteBuffer] instance from different threads.
 */
@InternalApi
class SdkByteBuffer internal constructor(
    // we make use of ktor-io's `Memory` type which already implements most of the functionality in a platform
    // agnostic way. We just need to wrap some methods around it
    internal var memory: Memory,
    val isReadOnly: Boolean = false,
    val allowReallocation: Boolean = true
) : Buffer, MutableBuffer {
    constructor(initialCapacity: ULong, readOnly: Boolean = false, allowReallocation: Boolean = true) : this(DefaultAllocator.alloc(initialCapacity), readOnly, allowReallocation)

    companion object {
        /**
         * Create an SdkBuffer backed by the given ByteArray.
         *
         * NOTE: Care should be taken if using this as a writable buffer to not exceed the capacity. Doing so will
         * cause a [FixedBufferSizeException] to be thrown.
         *
         * @param src the ByteArray to wrap
         * @param offset the offset into the array that will mark the first byte written/read
         * @param length the number of bytes to make available for reading/writing
         */
        fun of(
            src: ByteArray,
            offset: Int = 0,
            length: Int = src.size - offset,
        ): SdkByteBuffer = SdkByteBuffer(Memory.ofByteArray(src, offset, length), allowReallocation = false)
    }

    private val state = SdkBufferState()

    /**
     * The total capacity of the buffer
     */
    val capacity: ULong
        get() = memory.size.toULong()

    /**
     * The current read position. Always non-negative and <= [writePosition].
     */
    val readPosition: ULong
        get() = state.readHead

    /**
     * The current write position. Will never run ahead of [capacity] and always greater than [readPosition]
     */
    val writePosition: ULong
        get() = state.writeHead

    /**
     * Number of bytes available for reading
     */
    override val readRemaining: ULong
        get() = writePosition - readPosition

    /**
     * Free space left for writing
     */
    override val writeRemaining: ULong
        get() = capacity - writePosition

    /**
     * Reserve capacity for at least [count] bytes to be written.
     *
     * More than [count] bytes may be allocated in order to avoid frequent re-allocations.
     */
    fun reserve(count: Long) {
        if (writeRemaining >= count.toULong()) return
        if (!allowReallocation) throw FixedBufferSizeException("SdkBuffer is of fixed size, cannot satisfy request to reserve $count bytes; writeRemaining: $writeRemaining")

        val minP2 = ceilp2(count.toULong() + writePosition)
        val currP2 = ceilp2(memory.size.toULong() + writePosition + 1u)
        val newSize = maxOf(minP2, currP2)
        memory = DefaultAllocator.realloc(memory, newSize)
    }

    /**
     * Discard [count] readable bytes
     */
    override fun discard(count: ULong): ULong {
        require(count >= 0u) { "cannot discard $count bytes; amount must be positive" }
        val size = minOf(count, readRemaining)
        state.readHead += size
        return size
    }

    /**
     * Rewind [readPosition] making [count] bytes available for reading again
     */
    fun rewind(count: ULong = readPosition) {
        val size = minOf(count, readPosition)
        state.readHead -= size
    }

    /**
     * Reset [readPosition] and [writePosition] making all bytes available for write and no bytes available to read
     */
    fun reset() {
        state.readHead = 0u
        state.writeHead = 0u
    }

    /**
     * Mark [n] bytes written and advance the [writePosition] by the same amount
     */
    override fun advance(n: ULong) {
        require(n <= writeRemaining) { "Unable to write $n bytes; only $writeRemaining write capacity left" }
        state.writeHead += n
    }

    /**
     * Read from this buffer exactly [length] bytes and write to [dest] starting at [offset]
     * @throws IllegalArgumentException if there are not enough bytes available for read or the offset/length combination is invalid
     */
    override fun readFully(dest: ByteArray, offset: Int, length: Int) {
        require(readRemaining >= length.toULong()) { "Not enough bytes to read a ByteArray of size $length" }
        require(offset >= 0) { "Invalid read offset, must be positive: $offset" }
        require(offset + length <= dest.size) { "Invalid read: offset + length should be less than the destination size: $offset + $length < ${dest.size}" }
        read { memory, readStart, _ ->
            memory.loadByteArray(readStart, dest, offset, length)
            length.toLong()
        }
    }

    /**
     * Read all available bytes from this buffer into [dest] starting at [offset] up to the destination buffers size.
     * If the total bytes available is less than [length] then as many bytes as are available will be read.
     * The total bytes read is returned or `-1` if no data is available.
     */
    override fun readAvailable(dest: ByteArray, offset: Int, length: Int): Int {
        if (!canRead) return -1

        val rc = minOf(length.toULong(), readRemaining, Int.MAX_VALUE.toULong())
        readFully(dest, offset, rc.toInt())
        return rc.toInt()
    }

    /**
     * Write [length] bytes of [src] to this buffer starting at [offset]
     * @throws IllegalArgumentException if there is insufficient space or the offset/length combination is invalid
     */
    override fun writeFully(src: ByteArray, offset: Int, length: Int) {
        require(offset >= 0) { "Invalid write offset, must be positive" }
        require(offset + length <= src.size) { "Invalid write: offset + length should be less than the source size: $offset + $length < ${src.size}" }
        writeSized(length.toLong()) { memory, writeStart ->
            memory.storeByteArray(writeStart, src, offset, length)
            length.toLong()
        }
    }

    /**
     * Read a single byte from the buffer
     */
    override fun readByte(): Byte {
        val value = memory.loadAt(readPosition.toLong())
        discard(1u)
        return value
    }

    /**
     * Write a single byte to the buffer
     */
    override fun writeByte(value: Byte) {
        memory.storeAt(writePosition.toLong(), value)
        advance(1u)
    }

    /**
     * Read a signed 16-bit integer in big-endian byte order
     */
    override fun readShort(): Short {
        val value = memory.loadShortAt(readPosition.toLong())
        discard(2u)
        return value
    }

    /**
     * Read an unsigned 16-bit integer in big-endian byte order
     */
    override fun readUShort(): UShort {
        val value = memory.loadUShortAt(readPosition.toLong())
        discard(2u)
        return value
    }

    /**
     * Write a signed 16-bit integer in big-endian byte order
     */
    override fun writeShort(value: Short) {
        memory.storeShortAt(writePosition.toLong(), value)
        advance(2u)
    }

    /**
     * Write an unsigned 16-bit integer in big-endian byte order
     */
    override fun writeUShort(value: UShort) {
        memory.storeUShortAt(writePosition.toLong(), value)
        advance(2u)
    }

    /**
     * Read a signed 32-bit integer in big-endian byte order
     */
    override fun readInt(): Int {
        val value = memory.loadIntAt(readPosition.toLong())
        discard(4u)
        return value
    }

    /**
     * Read an unsigned 32-bit integer in big-endian byte order
     */
    override fun readUInt(): UInt {
        val value = memory.loadUIntAt(readPosition.toLong())
        discard(4u)
        return value
    }

    /**
     * Write a signed 32-bit integer in big-endian byte order
     */
    override fun writeInt(value: Int) {
        memory.storeIntAt(writePosition.toLong(), value)
        advance(4u)
    }

    /**
     * Write an unsigned 32-bit integer in big-endian byte order
     */
    override fun writeUInt(value: UInt) {
        memory.storeUIntAt(writePosition.toLong(), value)
        advance(4u)
    }

    /**
     * Read a signed 64-bit integer in big-endian byte order
     */
    override fun readLong(): Long {
        val value = memory.loadLongAt(readPosition.toLong())
        discard(8u)
        return value
    }

    /**
     * Read an unsigned 64-bit integer in big-endian byte order
     */
    override fun readULong(): ULong {
        val value = memory.loadULongAt(readPosition.toLong())
        discard(8u)
        return value
    }

    /**
     * Write a signed 64-bit integer in big-endian byte order
     */
    override fun writeLong(value: Long) {
        memory.storeLongAt(writePosition.toLong(), value)
        advance(8u)
    }

    /**
     * Write an unsigned 64-bit integer in big-endian byte order
     */
    override fun writeULong(value: ULong) {
        memory.storeULongAt(writePosition.toLong(), value)
        advance(8u)
    }

    /**
     * Read a 32-bit float in big-endian byte order
     */
    override fun readFloat(): Float {
        val value = memory.loadFloatAt(readPosition.toLong())
        discard(4u)
        return value
    }

    /**
     * Write a 32-bit float in big-endian byte order
     */
    override fun writeFloat(value: Float) {
        memory.storeFloatAt(writePosition.toLong(), value)
        advance(4u)
    }

    /**
     * Read a 64-bit double in big-endian byte order
     */
    override fun readDouble(): Double {
        val value = memory.loadDoubleAt(readPosition.toLong())
        discard(8u)
        return value
    }
    /**
     * Write a 32-bit float in big-endian byte order
     */
    override fun writeDouble(value: Double) {
        memory.storeDoubleAt(writePosition.toLong(), value)
        advance(8u)
    }
}

/**
 * @return `true` if there are bytes to be read
 */
public inline val SdkByteBuffer.canRead: Boolean
    get() = writePosition > readPosition

/**
 * Creates a new, read-only byte buffer that shares this buffer's content.
 * Any attempts to write to the buffer will fail with [ReadOnlyBufferException]
 */
fun SdkByteBuffer.asReadOnly(): SdkByteBuffer = if (isReadOnly) this else SdkByteBuffer(memory, isReadOnly = true)

/**
 * Reads at most [length] bytes from this buffer into the [dst] buffer
 * @return the number of bytes read
 */
fun SdkByteBuffer.readFully(dst: SdkByteBuffer, length: ULong = dst.writeRemaining): ULong {
    require(length <= Int.MAX_VALUE.toULong()) { "Unable to satisfy read request for $length bytes" }
    val rc = minOf(readRemaining, length).toLong()
    if (rc == 0L) return 0UL
    return read { memory, readStart, _ ->
        dst.reserve(rc)
        memory.copyTo(dst.memory, readStart, rc, dst.writePosition.toLong())
        dst.advance(rc.toULong())
        rc
    }.toULong()
}

/**
 * Reads at most [length] bytes from this buffer or `-1` if no bytes are available for read.
 * @return the number of bytes read or -1 if the buffer is empty
 */
fun SdkByteBuffer.readAvailable(dst: SdkByteBuffer, length: ULong = dst.writeRemaining): ULong? {
    if (!canRead) return null
    val rc = minOf(readRemaining, length)
    return readFully(dst, rc)
}

/**
 * Write exactly [length] bytes from [src] to this buffer
 */
fun SdkByteBuffer.writeFully(src: SdkByteBuffer, length: ULong = src.readRemaining) {
    require(length <= src.readRemaining) {
        "not enough bytes in source buffer to read $length bytes (${src.readRemaining} remaining)"
    }
    // in practice readRemaining will always be <= Long.MAX_VALUE due to underlying constraints around Memory type
    check(length <= Long.MAX_VALUE.toULong())
    val len = length.toLong()

    writeSized(len) { memory, writeStart ->
        src.memory.copyTo(memory, src.readPosition.toLong(), len, writeStart)
        src.discard(length).toLong()
    }
}

/**
 * Write the bytes of [str] as UTF-8
 */
// TODO - remove in favor of implementing Appendable in such a way as to not allocate an entire new byte array
fun SdkByteBuffer.write(str: String) = writeFully(str.encodeToByteArray())

/**
 * Read the available (unread) contents as a UTF-8 string
 */
fun SdkByteBuffer.decodeToString() = bytes().decodeToString(0, readRemaining.toInt())

/**
 * Get the available (unread) contents as a ByteArray.
 *
 * NOTE: This may or may not create a new backing array and perform a copy depending on the platform
 * and current buffer status.
 */
expect fun SdkByteBuffer.bytes(): ByteArray

internal inline fun SdkByteBuffer.read(block: (memory: Memory, readStart: Long, endExclusive: Long) -> Long): Long {
    val rc = block(memory, readPosition.toLong(), writePosition.toLong())
    discard(rc.toULong())
    return rc
}

internal inline fun SdkByteBuffer.write(block: (memory: Memory, writeStart: Long, endExclusive: Long) -> Long): Long {
    if (isReadOnly) throw ReadOnlyBufferException("attempt to write to readOnly buffer at index: $writePosition")

    val wc = block(memory, writePosition.toLong(), capacity.toLong())
    advance(wc.toULong())
    return wc
}

internal inline fun SdkByteBuffer.writeSized(count: Long, block: (memory: Memory, writeStart: Long) -> Long): Long {
    reserve(count)
    return write { memory, writeStart, _ ->
        block(memory, writeStart)
    }
}
