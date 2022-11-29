/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.toOkio
import okio.buffer

/**
 * Returns a new sink that buffers writes to the sink. Writes will be efficiently "batched".
 * Call [SdkSink.flush] when done to emit all data to the underlying sink.
 */
public fun SdkSink.buffer(): SdkBufferedSink = when (this) {
    is SdkBufferedSink -> this
    else -> BufferedSinkAdapter(toOkio().buffer())
}

/**
 * Returns a new source that buffers reads from the underlying source. The returned source
 * will perform bulk reads to an in-memory buffer making small reads efficient.
 */
public fun SdkSource.buffer(): SdkBufferedSource = when (this) {
    is SdkBufferedSource -> this
    else -> BufferedSourceAdapter(toOkio().buffer())
}

/**
 * Returns a new source that reads from the underlying [ByteArray]
 */
public fun ByteArray.source(): SdkSource = ByteArraySource(this)

private class ByteArraySource(
    private val data: ByteArray,
) : SdkSource {
    private var offset = 0
    override fun read(sink: SdkBuffer, limit: Long): Long {
        if (offset >= data.size) return -1L

        val rc = minOf(limit, data.size.toLong() - offset.toLong())
        sink.write(data, offset, rc.toInt())
        offset += rc.toInt()

        return rc
    }
    override fun close() {}
}
