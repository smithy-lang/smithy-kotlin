/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

public actual sealed interface SdkBufferedSink : SdkSink {
    /**
     * The underlying buffer for this sink
     */
    public actual val buffer: SdkBuffer

    /**
     * Write [limit] bytes from [source] starting at [offset]
     */
    public actual fun write(source: ByteArray, offset: Int, limit: Int)

    /**
     * Removes [byteCount] bytes from [source] and writes them to this sink.
     */
    public actual fun write(source: SdkSource, byteCount: Long)

    /**
     * Write all bytes from [source] to this sink.
     * @return the number of bytes read which will be 0 if [source] is exhausted
     */
    public actual fun writeAll(source: SdkSource): Long

    /**
     * Write [string] as UTF-8 encoded bytes to this sink starting at [start] index up to [endExclusive] index.
     */
    public actual fun writeUtf8(string: String, start: Int, endExclusive: Int)

    /**
     * Writes byte [x] to this sink
     */
    public actual fun writeByte(x: Byte)

    /**
     * Writes short [x] as a big-endian bytes to this sink
     */
    public actual fun writeShort(x: Short)

    /**
     * Writes short [x] as a little-endian bytes to this sink
     */
    public actual fun writeShortLe(x: Short)

    /**
     * Writes int [x] as a big-endian bytes to this sink
     */
    public actual fun writeInt(x: Int)

    /**
     * Writes int [x] as a little-endian bytes to this sink
     */
    public actual fun writeIntLe(x: Int)

    /**
     * Writes long [x] as a big-endian bytes to this sink
     */
    public actual fun writeLong(x: Long)

    /**
     * Writes long [x] as a little-endian bytes to this sink
     */
    public actual fun writeLongLe(x: Long)

    /**
     * Writes all buffered data to the underlying sink. Like flush, but weaker (ensures data is pushed to the
     * underlying sink but not necessarily all the way down the chain like [flush] does). Call before this sink
     * goes out of scope to ensure any buffered data eventually gets to its final destination
     */
    public actual fun emit()
}
