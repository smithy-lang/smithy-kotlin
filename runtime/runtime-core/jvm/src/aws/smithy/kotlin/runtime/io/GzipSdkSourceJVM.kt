/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import java.util.zip.GZIPOutputStream

/**
 * Wraps the SdkSource so that it compresses into gzip format with each read.
 */
@InternalApi
public actual class GzipSdkSource actual constructor(
    private val source: SdkSource,
) : SdkSource {
    private val gzipBuffer = SdkBuffer()
    private val gzipOutputStream = GZIPOutputStream(gzipBuffer.outputStream(), true)

    override fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L

        val temp = SdkBuffer()
        val rc = source.read(temp, limit)

        if (rc == -1L) {
            // may trigger additional bytes written by gzip defalter
            gzipOutputStream.close()
            gzipBuffer.readAll(sink)
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

    override fun close() {
        gzipOutputStream.close()
        source.close()
    }
}
