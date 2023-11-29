/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import java.util.zip.GZIPOutputStream

@InternalApi
public class GzipByteReadChannel(
    private val source: SdkByteReadChannel,
) : SdkByteReadChannel by source {
    private val gzipBuffer = SdkBuffer()
    private val gzipOutputStream = GZIPOutputStream(gzipBuffer.outputStream())

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L

        val temp = SdkBuffer()
        val rc = source.read(temp, limit)

        return if (rc >= 0L) {
            gzipOutputStream.write(temp.readByteArray())
            gzipBuffer.readAll(sink)
            rc
        } else {
            require(rc == -1L)
            gzipOutputStream.write(temp.readByteArray())
            gzipOutputStream.close()
            gzipBuffer.readAll(sink)
            rc
        }
    }
}
