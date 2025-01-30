/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.compression.GzipCompressor

/**
 * Wraps an [SdkSource], compressing bytes read into GZIP format.
 * @param source the [SdkSource] to compress the contents of
 */
@InternalApi
public actual class GzipSdkSource actual constructor(public val source: SdkSource) : SdkSource {
    private val compressor = GzipCompressor()

    actual override fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L
        if (compressor.isClosed) return -1L

        // If no compressed bytes are available, attempt to refill the compressor
        if (compressor.availableForRead == 0) {
            val temp = SdkBuffer()
            val rc = source.read(temp, GzipCompressor.BUFFER_SIZE.toLong())

            if (rc > 0) {
                val input = temp.readByteArray(rc)
                compressor.update(input)
            }
        }

        // If still no data is available, we've hit EOF. Close the compressor and write the remaining bytes
        if (compressor.availableForRead == 0) {
            val terminationBytes = compressor.flush()
            sink.write(terminationBytes)
            return terminationBytes.size.toLong()
        }

        // Read compressed bytes from the compressor
        val bytesToRead = minOf(limit, compressor.availableForRead.toLong())
        val compressed = compressor.consume(bytesToRead.toInt())
        sink.write(compressed)
        return compressed.size.toLong()
    }

    actual override fun close() {
        compressor.flush()
        source.close()
    }
}
