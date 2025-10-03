/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.compression.GzipCompressor

/**
 * Wraps an [SdkByteReadChannel], compressing bytes read into GZIP format.
 * @param channel the [SdkByteReadChannel] to compress the contents of
 */
@InternalApi
public actual class GzipByteReadChannel actual constructor(public val channel: SdkByteReadChannel) : SdkByteReadChannel {
    private val compressor = GzipCompressor()

    actual override val availableForRead: Int
        get() = compressor.availableForRead

    actual override val isClosedForRead: Boolean
        get() = channel.isClosedForRead && compressor.isClosed

    actual override val isClosedForWrite: Boolean
        get() = channel.isClosedForWrite

    actual override val closedCause: Throwable?
        get() = channel.closedCause

    actual override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L
        if (compressor.isClosed) return -1L

        // If no compressed bytes are available, attempt to refill the compressor
        if (compressor.availableForRead == 0 && !channel.isClosedForRead) {
            val temp = SdkBuffer()
            val rc = channel.read(temp, GzipCompressor.BUFFER_SIZE.toLong())

            if (rc > 0) {
                val input = temp.readByteArray(rc)
                compressor.update(input)
            }
        }

        // If still no data is available and the channel is closed, we've hit EOF. Close the compressor and write the remaining bytes
        if (compressor.availableForRead == 0 && channel.isClosedForRead) {
            val terminationBytes = compressor.flush()
            sink.write(terminationBytes)
            return terminationBytes.size.toLong().also {
                compressor.close()
            }
        }

        // Read compressed bytes from the compressor
        val bytesToRead = minOf(limit, compressor.availableForRead.toLong())
        val compressed = compressor.consume(bytesToRead.toInt())
        sink.write(compressed)
        return compressed.size.toLong()
    }

    actual override fun cancel(cause: Throwable?): Boolean {
        compressor.close()
        return channel.cancel(cause)
    }
}
