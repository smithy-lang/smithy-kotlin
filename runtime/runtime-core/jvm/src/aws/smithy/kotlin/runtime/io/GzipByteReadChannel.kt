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

    override suspend fun read(sink: SdkBuffer, limit: Long): Long { // TODO: Fix this
        // Read "limit" bytes into byte array
        val converter = SdkBuffer()
        val bytesRead = source.read(converter, limit)
        val byteArray = converter.readByteArray()
        converter.close()

        // Pass byteArray to gzip
        gzipOutputStream.write(byteArray)

        // Extract compressed byteArray into function sink
        gzipBuffer.readAll(sink)
        if (bytesRead == -1L) {
            gzipOutputStream.close()
            gzipBuffer.close()
        }

        // Returns amount of bytes read from source
        return bytesRead
    }
}
