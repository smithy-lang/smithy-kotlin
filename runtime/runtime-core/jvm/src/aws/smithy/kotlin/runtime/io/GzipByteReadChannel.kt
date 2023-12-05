/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import java.util.zip.GZIPOutputStream

@InternalApi
public class GzipByteReadChannel(
    private val channel: SdkByteReadChannel,
) : SdkByteReadChannel by channel {
    private val gzipBuffer = SdkBuffer()
    private val gzipOutputStream = GZIPOutputStream(gzipBuffer.outputStream())

    /**
     * Keeps track of whether a read operation has been made on this byte read channel
     */
    private var read: Boolean = false

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L

        if (isClosedForRead) {
            if (!read) { // Empty payload
                gzipOutputStream.write(ByteArray(0))
                gzipOutputStream.close()
                gzipBuffer.readAll(sink)
                gzipBuffer.close()

                read = true
            }

            return -1L
        }

        if (!read) read = true

        val temp = SdkBuffer()
        val rc = channel.read(temp, limit)

        gzipOutputStream.write(temp.readByteArray())
        gzipBuffer.readAll(sink)

        if (isClosedForRead) {
            gzipOutputStream.close()
            gzipBuffer.readAll(sink)
            gzipBuffer.close()
        }

        temp.close()

        return rc
    }

    override fun cancel(cause: Throwable?): Boolean {
        gzipOutputStream.close()
        gzipBuffer.close()

        return channel.cancel(cause)
    }
}
