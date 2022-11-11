/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

/**
 * A source for reading a stream of bytes (e.g. from file, network, or in-memory buffer). Sources may
 * be layered to transform data as it is read (e.g. to decompress, decrypt, or remove protocol framign).
 *
 * Most application code should not operate on a source directly, but rather a [SdkBufferedSource] which is
 * more convenient. Use [SdkSource.buffer] to wrap any source with a buffer.
 *
 * ### Thread/Coroutine Safety
 *
 * Sources are not thread safe by default. Do not share a source between threads or coroutines without external
 * synchronization.
 */
public interface SdkSource : Closeable {
    /**
     * Remove at least 1 byte, and up-to [limit] bytes from this and appends them to [sink].
     * Returns the number of bytes read, or -1 if this source is exhausted.
     */
    @Throws(IOException::class)
    public fun read(sink: SdkBuffer, limit: Long): Long

    /**
     * Closes this source and releases any resources held. It is an error to read from a closed
     * source. This is an idempotent operation.
     */
    @Throws(IOException::class)
    override fun close()
}
