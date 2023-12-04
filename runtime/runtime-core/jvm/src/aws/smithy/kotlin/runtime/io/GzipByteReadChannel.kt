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

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L

        if (isClosedForRead) return -1

        val temp = SdkBuffer()
        val rc = channel.read(temp, limit)

        gzipOutputStream.write(temp.readByteArray())
        gzipBuffer.readAll(sink)

        if (isClosedForRead) {
            gzipOutputStream.close()
            gzipBuffer.readAll(sink)
            gzipBuffer.close()
        }

        return rc
    }

    override fun cancel(cause: Throwable?): Boolean {
        gzipOutputStream.close()
        gzipBuffer.close()

        return channel.cancel(cause)
    }
}
