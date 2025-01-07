/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.compression.gzipCompressBytes

/**
 * Wraps an [SdkSource], compressing bytes read into GZIP format.
 * @param source the [SdkSource] to compress the contents of
 */
@InternalApi
public actual class GzipSdkSource actual constructor(public val source: SdkSource) : SdkSource {
    actual override fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L

        val temp = SdkBuffer()
        val rc = source.read(temp, limit)

        if (rc <= 0L) {
            return rc
        }

        val compressed = gzipCompressBytes(temp.readByteArray())
        sink.write(compressed)
        return compressed.size.toLong()
    }

    actual override fun close() {
        source.close()
    }
}
