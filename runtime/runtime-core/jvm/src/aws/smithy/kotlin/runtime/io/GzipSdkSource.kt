/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import java.util.zip.GZIPOutputStream

@InternalApi
public class GzipSdkSource(
    private val source: SdkSource,
    private val bytesAvailable: Int?,
) : SdkSource {
    private val gzipBuffer = SdkBuffer()
    private val gzipOutputStream = GZIPOutputStream(gzipBuffer.outputStream(), true)
    private var bytesRead: Int = 0

    override fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L

        if (bytesRead == bytesAvailable) return -1

        val temp = SdkBuffer()
        val rc = source.read(temp, limit)

        gzipOutputStream.write(temp.readByteArray())
        gzipBuffer.readAll(sink)

        bytesRead += rc.toInt()

        if (bytesRead == bytesAvailable || rc == -1L) {
            gzipOutputStream.close()
            gzipBuffer.readAll(sink)
            gzipBuffer.close()
        }

        return rc
    }

    override fun close() {
        gzipOutputStream.close()
        gzipBuffer.close()
        source.close()
    }
}
