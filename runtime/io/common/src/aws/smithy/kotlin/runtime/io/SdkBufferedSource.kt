/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

// TODO - decide if we want to expose any readFully() methods

/**
 * A source that keeps a buffer internally such that small reads are
 * performant. It also allows peaking ahead, buffering data as necessary,
 * before consuming it.
 */
public expect sealed interface SdkBufferedSource : SdkSource {

    /**
     * The underlying buffer for this source
     */
    public val buffer: SdkBuffer

    /**
     * Discards [byteCount] bytes from this source. Throws [IOException] if source is exhausted before [byteCount]
     * bytes can be discarded.
     */
    public fun skip(byteCount: Long): Unit

    /**
     * Read a single byte from this source and return it
     */
    public fun readByte(): Byte

    /**
     * Read two bytes from this source and returns a big-endian short.
     */
    public fun readShort(): Short

    /**
     * Read two bytes from this source and returns it as a little-endian short.
     */
    public fun readShortLe(): Short

    /**
     * Read four bytes from this source and returns a big-endian long.
     */
    public fun readLong(): Long

    /**
     * Read four bytes from this source and returns a little-endian long.
     */
    public fun readLongLe(): Long

    /**
     * Read four bytes from this source and returns a big-endian int.
     */
    public fun readInt(): Int

    /**
     * Read four bytes from this source and returns a little-endian int.
     */
    public fun readIntLe(): Int

    /**
     * Reads all bytes from this and appends them to [sink]. Returns
     * the total number of bytes written which will be 0 if this source
     * is exhausted.
     */
    public fun readAll(sink: SdkSink): Long

    /**
     * Read up to [limit] bytes and write them to [sink] starting at [offset]
     */
    public fun read(sink: ByteArray, offset: Int = 0, limit: Int = sink.size - offset): Int

    /**
     * Reads all bytes from this source and returns them as a byte array
     *
     * **Caution** This may pull a large amount of data into memory, only do this if you are sure
     * the contents fit into memory. Throws [IllegalArgumentException] if the buffer size exceeds [Int.MAX_VALUE].
     */
    public fun readByteArray(): ByteArray

    /**
     * Reads [byteCount] bytes from this source and returns them as a byte array
     */
    public actual fun readByteArray(byteCount: Long): ByteArray

    /**
     * Reads all bytes from this, decodes them as UTF-8, and returns the string.
     */
    public fun readUtf8(): String

    /**
     * Reads [byteCount] bytes from this, decodes them as UTF-8, and returns the string.
     *
     * **Caution** This may pull a large amount of data into memory, only do this if you are sure
     * the contents fit into memory. Throws [IllegalArgumentException] if the buffer size exceeds [Int.MAX_VALUE].
     */
    public fun readUtf8(byteCount: Long): String

    /**
     * Returns a new [SdkBufferedSource] that can read data from this source
     * without consuming it. The returned source becomes invalid once this source is next
     * read or closed.
     */
    public fun peek(): SdkBufferedSource

    /**
     * Returns true when the buffer contains at least [byteCount] bytes. False if the source
     * is exhausted before the requested number of bytes could be read
     */
    public fun request(byteCount: Long): Boolean

    /**
     * Returns when the buffer contains at least [byteCount] bytes or throws [EOFException]
     * if the source is exhausted before the requested number of bytes could be read
     */
    public fun require(byteCount: Long)
}
