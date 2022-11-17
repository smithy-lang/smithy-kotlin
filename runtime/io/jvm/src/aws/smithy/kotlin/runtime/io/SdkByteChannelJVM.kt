/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read all bytes from this channel into [sink]. Returns the total number of bytes written.
 */
public suspend fun SdkByteReadChannel.readAll(sink: SdkSink): Long = withContext(Dispatchers.IO) {
    val bufferedSink = if (sink is SdkBuffer) sink else sink.buffer()
    var totalWritten = 0L
    while (true) {
        val rc = read(bufferedSink.buffer, DEFAULT_BYTE_CHANNEL_MAX_BUFFER_SIZE.toLong())
        if (rc == -1L) break
        totalWritten += rc
        bufferedSink.emit()
    }
    bufferedSink.emit()
    totalWritten
}

/**
 * Removes all bytes from [source] and writes them to this channel. Returns the total number of bytes read.
 */
public suspend fun SdkByteWriteChannel.writeAll(source: SdkSource): Long = withContext(Dispatchers.IO) {
    val buffer = SdkBuffer()
    var totalRead = 0L
    while (true) {
        val rc = source.read(buffer, DEFAULT_BYTE_CHANNEL_MAX_BUFFER_SIZE.toLong())
        if (rc == -1L) break
        totalRead += rc
        write(buffer)
    }
    totalRead
}
