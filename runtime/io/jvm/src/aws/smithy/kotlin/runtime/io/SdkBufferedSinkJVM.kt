/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import java.io.OutputStream
import java.nio.channels.WritableByteChannel
import kotlin.jvm.Throws

/**
 * A sink that keeps a buffer internally so that callers can do small writes without
 * a performance penalty.
 */
public actual sealed interface SdkBufferedSink : SdkSink, WritableByteChannel {
    /**
     * The underlying buffer for this sink
     */
    public actual val buffer: SdkBuffer

    /**
     * Write [limit] bytes from [source] starting at [offset]
     */
    @Throws(IOException::class)
    public actual fun write(source: ByteArray, offset: Int, limit: Int): Unit

    /**
     * Write all bytes from [source] to this sink.
     * @return the number of bytes read which will be 0 if [source] is exhausted
     */
    @Throws(IOException::class)
    public actual fun writeAll(source: SdkSource): Long

    /**
     * Removes [byteCount] bytes from [source] and writes them to this sink.
     */
    @Throws(IOException::class)
    public actual fun write(source: SdkSource, byteCount: Long): Unit

    /**
     * Write UTF8-bytes of [string] to this sink starting at [start] index up to [endExclusive] index.
     */
    @Throws(IOException::class)
    public actual fun writeUtf8(string: String, start: Int, endExclusive: Int): Unit

    /**
     * Writes byte [x] to this sink
     */
    @Throws(IOException::class)
    public actual fun writeByte(x: Byte): Unit

    /**
     * Writes short [x] as a big-endian bytes to this sink
     */
    @Throws(IOException::class)
    public actual fun writeShort(x: Short): Unit

    /**
     * Writes short [x] as a little-endian bytes to this sink
     */
    @Throws(IOException::class)
    public actual fun writeShortLe(x: Short): Unit

    /**
     * Writes int [x] as a big-endian bytes to this sink
     */
    @Throws(IOException::class)
    public actual fun writeInt(x: Int): Unit

    /**
     * Writes int [x] as a little-endian bytes to this sink
     */
    @Throws(IOException::class)
    public actual fun writeIntLe(x: Int): Unit

    /**
     * Writes long [x] as a big-endian bytes to this sink
     */
    @Throws(IOException::class)
    public actual fun writeLong(x: Long): Unit

    /**
     * Writes long [x] as a little-endian bytes to this sink
     */
    @Throws(IOException::class)
    public actual fun writeLongLe(x: Long): Unit

    /**
     * Return an output stream that writes to this sink
     */
    public fun outputStream(): OutputStream

    /**
     * Writes all buffered data to the underlying sink.
     */
    @Throws(IOException::class)
    actual override fun flush(): Unit

    /**
     * Writes all buffered data to the underlying sink. Like flush, but weaker (ensures data is pushed to the
     * underlying sink but not necessarily all the way down the chain like [flush] does). Call before this sink
     * goes out of scope to ensure any buffered data eventually gets to its final destination
     */
    @Throws(IOException::class)
    public actual fun emit(): Unit
}
