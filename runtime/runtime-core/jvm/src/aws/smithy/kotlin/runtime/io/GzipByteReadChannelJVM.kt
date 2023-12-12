/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import java.util.zip.GZIPOutputStream

/**
 * Wraps the SdkByteReadChannel so that it compresses into gzip format with each read.
 */
@InternalApi
public actual class GzipByteReadChannel actual constructor(
    private val channel: SdkByteReadChannel,
) : SdkByteReadChannel by channel {
    private val gzipBuffer = SdkBuffer()
    private val gzipOutputStream = GZIPOutputStream(gzipBuffer.outputStream(), true)
    private var gzipOutputStreamClosed = false

    override val availableForRead: Int
        get() = gzipBuffer.size.toInt()

    override val isClosedForRead: Boolean
        get() = channel.isClosedForRead && gzipBuffer.exhausted() && gzipOutputStreamClosed

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L

        val temp = SdkBuffer()
        val rc = channel.read(temp, limit)

        if (rc == -1L) {
            // may trigger additional bytes written by gzip defalter
            gzipOutputStream.close()
            gzipBuffer.readAll(sink)

            gzipOutputStreamClosed = true
        }

        // source is exhausted and nothing left buffered we are done
        if (rc == -1L && gzipBuffer.exhausted()) {
            return -1L
        }

        if (rc >= 0L) {
            gzipOutputStream.write(temp.readByteArray())
            gzipOutputStream.flush()
        }

        // read bytes read from compressed content
        return gzipBuffer.read(sink, limit)
    }

    override fun cancel(cause: Throwable?): Boolean {
        gzipOutputStream.close()
        return channel.cancel(cause)
    }
}
