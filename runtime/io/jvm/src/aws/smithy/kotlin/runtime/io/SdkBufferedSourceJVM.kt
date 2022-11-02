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
     * Discards [byteCount] bytes from this source. Throws
     */
    @Throws(IOException::class)
    public actual fun skip(byteCount: Long)

    /**
     * Read a single byte from this source and return it
     */
    @Throws(IOException::class)
    public actual fun readByte(): Byte

    /**
     * Read two bytes from this source and returns a big-endian short.
     */
    @Throws(IOException::class)
    public actual fun readShort(): Short

    /**
     * Read two bytes from this source and returns it as a little-endian short.
     */
    @Throws(IOException::class)
    public actual fun readShortLe(): Short

    /**
     * Read four bytes from this source and returns a big-endian long.
     */
    @Throws(IOException::class)
    public actual fun readLong(): Long

    /**
     * Read four bytes from this source and returns a little-endian long.
     */
    @Throws(IOException::class)
    public actual fun readLongLe(): Long

    /**
     * Read four bytes from this source and returns a big-endian int.
     */
    @Throws(IOException::class)
    public actual fun readInt(): Int

    /**
     * Read four bytes from this source and returns a little-endian int.
     */
    @Throws(IOException::class)
    public actual fun readIntLe(): Int

    /**
     * Removes all bytes from this and appends them to [sink]. Returns
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
     * Removes all bytes from this source and returns them as a byte array
     */
    @Throws(IOException::class)
    public actual fun readByteArray(): ByteArray

    /**
     * Removes [byteCount] bytes from this source and returns them as a byte array
     */
    @Throws(IOException::class)
    public actual fun readByteArray(byteCount: Long): ByteArray

    /**
     * Removes all bytes from this, decodes them as UTF-8, and returns the string.
     */
    @Throws(IOException::class)
    public actual fun readUtf8(): String

    /**
     * Removes [byteCount] bytes from this, decodes them as UTF-8, and returns the string.
     */
    @Throws(IOException::class)
    public actual fun readUtf8(byteCount: Long): String

    /**
     * Get an input stream that writes to this source
     */
    public fun inputStream(): InputStream

    /**
     * Returns a new [SdkBufferedSource] that can read data from this source
     * without consume it. The returned source becomes invalid once this source is next
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
