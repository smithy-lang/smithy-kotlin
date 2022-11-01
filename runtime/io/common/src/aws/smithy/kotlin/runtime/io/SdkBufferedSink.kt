/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

/**
 * A sink that keeps a buffer internally so that callers can do small writes without
 * a performance penalty.
 */
public expect sealed interface SdkBufferedSink : SdkSink {
    /**
     * The underlying buffer for this sink
     */
    public val buffer: SdkBuffer

    /**
     * Write [limit] bytes from [source] starting at [offset]
     */
    public fun write(source: ByteArray, offset: Int = 0, limit: Int = source.size - offset): Unit

    /**
     * Write all bytes from [source] to this sink.
     * @return the number of bytes read which will be 0 if [source] is exhausted
     */
    public fun writeAll(source: SdkSource): Long

    /**
     * Removes [byteCount] bytes from [source] and writes them to this sink.
     */
    public fun write(source: SdkSource, byteCount: Long): Unit

    /**
     * Write UTF8-bytes of [string] to this sink starting at [start] index up to [endExclusive] index.
     */
    public fun writeUtf8(string: String, start: Int = 0, endExclusive: Int = string.length): Unit

    /**
     * Writes byte [x] to this sink
     */
    public fun writeByte(x: Byte): Unit

    /**
     * Writes short [x] as a big-endian short to this sink
     */
    public fun writeShort(x: Short): Unit

    /**
     * Writes short [x] as a little-endian short to this sink
     */
    public fun writeShortLe(x: Short): Unit

    /**
     * Writes int [x] as a big-endian int to this sink
     */
    public fun writeInt(x: Int): Unit

    /**
     * Writes int [x] as a little-endian int to this sink
     */
    public fun writeIntLe(x: Int): Unit

    /**
     * Writes long [x] as a big-endian long to this sink
     */
    public fun writeLong(x: Long): Unit

    /**
     * Writes long [x] as a little-endian long to this sink
     */
    public fun writeLongLe(x: Long): Unit

    /**
     * Writes all buffered data to the underlying sink and pushes it recursively all the way
     * to its final destination.
     */
    override fun flush(): Unit

    /**
     * Writes all buffered data to the underlying sink. Like flush, but weaker. Call before this sink
     * goes out of scope to ensure any buffered data eventually gets to its final destination
     */
    public fun emit(): Unit
}
