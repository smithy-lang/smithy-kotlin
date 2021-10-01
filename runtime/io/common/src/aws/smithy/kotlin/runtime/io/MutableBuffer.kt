/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

/**
 * A mutable buffer that can be written to
 */
interface MutableBuffer {
    // TODO - could implement Appendable for convenience, need writeUtf8Char() extension

    /**
     * Free space left for writing. Implementations may allocate more memory on the fly in which case this
     * value should indicate how much remains between the buffer capacity and the write position.
     */
    val writeRemaining: ULong

    /**
     * Mark [n] bytes written and advance the internal writePosition by the same amount
     *
     * NOTE: This method does not guarantee that the bytes being advanced past have been initialized.
     * @throws IllegalArgumentException the bytes to advance [n] is greater than [writeRemaining]
     */
    fun advance(n: ULong)

    /**
     * Write a single byte to the buffer
     */
    fun writeByte(value: Byte)

    /**
     * Write a signed 16-bit integer in big-endian byte order
     */
    fun writeShort(value: Short)

    /**
     * Write an unsigned 16-bit integer in big-endian byte order
     */
    fun writeUShort(value: UShort)

    /**
     * Write a signed 32-bit integer in big-endian byte order
     */
    fun writeInt(value: Int)

    /**
     * Write an unsigned 32-bit integer in big-endian byte order
     */
    fun writeUInt(value: UInt)

    /**
     * Write a signed 64-bit integer in big-endian byte order
     */
    fun writeLong(value: Long)

    /**
     * Write an unsigned 64-bit integer in big-endian byte order
     */
    fun writeULong(value: ULong)

    /**
     * Write a 32-bit float in big-endian byte order
     */
    fun writeFloat(value: Float)

    /**
     * Write a 32-bit float in big-endian byte order
     */
    fun writeDouble(value: Double)

    /**
     * Write [length] bytes of [src] to this buffer starting at [offset]
     * @throws IllegalArgumentException if there is insufficient space or the offset/length combination is invalid
     */
    fun writeFully(src: ByteArray, offset: Int = 0, length: Int = src.size - offset)
}

/**
 * Write exactly [length] bytes from [src] to this buffer
 */
fun MutableBuffer.writeFully(src: SdkByteBuffer, length: ULong = src.readRemaining) {
    require(length <= src.readRemaining) {
        "not enough bytes in source buffer to read $length bytes (${src.readRemaining} remaining)"
    }
    if (this is SdkByteBuffer) return this.writeFully(src, length)

    TODO("Buffer.writeFully fallback not implemented for ${this::class}")
}
