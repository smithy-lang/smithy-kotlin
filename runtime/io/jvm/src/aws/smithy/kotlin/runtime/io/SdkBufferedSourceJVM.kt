/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import java.io.InputStream
import java.nio.channels.ReadableByteChannel

public actual sealed interface SdkBufferedSource : SdkSource, ReadableByteChannel {

    /**
     * The underlying buffer for this source
     */
    public actual val buffer: SdkBuffer

    /**
     * Discards [byteCount] bytes from this source. Throws [IOException] if source is exhausted before [byteCount]
     * bytes can be discarded.
     */
    @Throws(IOException::class)
    public actual fun skip(byteCount: Long)

    /**
     * Read a single byte from this source and return it
     */
    @Throws(IOException::class)
    public actual fun readByte(): Byte

    /**
     * Read two bytes in big-endian order from this source and returns them as a short.
     */
    @Throws(IOException::class)
    public actual fun readShort(): Short

    /**
     * Read two bytes in little-endian order from this source and returns them as a short.
     */
    @Throws(IOException::class)
    public actual fun readShortLe(): Short

    /**
     * Read eight bytes in big-endian order from this source and returns them as a long.
     */
    @Throws(IOException::class)
    public actual fun readLong(): Long

    /**
     * Read eight bytes in little-endian order from this source and returns them as a long.
     */
    @Throws(IOException::class)
    public actual fun readLongLe(): Long

    /**
     * Read four bytes in big-endian order from this source and returns them as an int.
     */
    @Throws(IOException::class)
    public actual fun readInt(): Int

    /**
     * Read four bytes in little-endian order from this source and returns them as an int.
     */
    @Throws(IOException::class)
    public actual fun readIntLe(): Int

    /**
     * Reads all bytes from this and appends them to [sink]. Returns
     * the total number of bytes written which will be 0 if this source
     * is exhausted.
     */
    @Throws(IOException::class)
    public actual fun readAll(sink: SdkSink): Long

    /**
     * Read up to [limit] bytes and write them to [sink] starting at [offset]
     */
    @Throws(IOException::class)
    public actual fun read(sink: ByteArray, offset: Int, limit: Int): Int

    /**
     * Reads all bytes from this source and returns them as a byte array
     *
     * **Caution** This may pull a large amount of data into memory, only do this if you are sure
     * the contents fit into memory. Throws [IllegalArgumentException] if the buffer size exceeds [Int.MAX_VALUE].
     */
    @Throws(IOException::class)
    public actual fun readByteArray(): ByteArray

    /**
     * Reads [byteCount] bytes from this source and returns them as a byte array
     */
    @Throws(IOException::class)
    public actual fun readByteArray(byteCount: Long): ByteArray

    /**
     * Reads all bytes from this source, decodes them as UTF-8, and returns the string.
     *
     * **Caution** This may pull a large amount of data into memory, only do this if you are sure
     * the contents fit into memory. Throws [IllegalArgumentException] if the buffer size exceeds [Int.MAX_VALUE].
     */
    @Throws(IOException::class)
    public actual fun readUtf8(): String

    /**
     * Reads [byteCount] bytes from this source, decodes them as UTF-8, and returns the string.
     */
    @Throws(IOException::class)
    public actual fun readUtf8(byteCount: Long): String

    /**
     * Get an input stream that writes to this source
     */
    public fun inputStream(): InputStream

    /**
     * Returns a new [SdkBufferedSource] that can read data from this source
     * without consuming it. The returned source becomes invalid once this source is next
     * read or closed.
     */
    public actual fun peek(): SdkBufferedSource

    /**
     * Returns true when the buffer contains at least [byteCount] bytes. False if the source
     * is exhausted before the requested number of bytes could be read
     */
    @Throws(IOException::class)
    public actual fun request(byteCount: Long): Boolean

    /**
     * Returns when the buffer contains at least [byteCount] bytes or throws [EOFException]
     * if the source is exhausted before the requested number of bytes could be read
     */
    @Throws(IOException::class)
    public actual fun require(byteCount: Long): Unit
}
