/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.compression.gzipCompressBytes

/**
 * Wraps an [SdkByteReadChannel], compressing bytes read into GZIP format.
 * @param channel the [SdkByteReadChannel] to compress the contents of
 */
@InternalApi
public actual class GzipByteReadChannel actual constructor(public val channel: SdkByteReadChannel) : SdkByteReadChannel {
    actual override val availableForRead: Int
        get() = channel.availableForRead

    actual override val isClosedForRead: Boolean
        get() = channel.isClosedForRead

    actual override val isClosedForWrite: Boolean
        get() = channel.isClosedForWrite

    actual override val closedCause: Throwable?
        get() = channel.closedCause

    actual override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L)
        if (limit == 0L) return 0L

        val temp = SdkBuffer()
        val rc = channel.read(temp, limit)

        if (rc <= 0) { return rc }

        val compressed = gzipCompressBytes(temp.readByteArray())
        sink.write(compressed)
        return compressed.size.toLong()
    }

    actual override fun cancel(cause: Throwable?): Boolean = channel.cancel(cause)
}
