/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import okio.Buffer
import okio.Timeout
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.jvm.Throws

internal class RandomAccessFileSource(
    private val fileObject: File,
    start: Long,
    private val endInclusive: Long,
    private val timeout: Timeout = Timeout.NONE,
) : okio.Source {
    private val file by lazy {
        require(fileObject.exists()) { "cannot create SdkSource, file does not exist: $this" }
        require(fileObject.isFile) { "cannot create a SdkSource from a directory: $this" }
        require(start >= 0L) { "start position should be >= 0, found $start" }
        require(endInclusive >= start - 1) {
            "end index $endInclusive must be greater than or equal to the start index minus one (${start-1})"
        }
        require(endInclusive <= fileObject.length() - 1) {
            "endInclusive should be less than or equal to the length of the file, was $endInclusive"
        }

        RandomAccessFile(fileObject, "r").also {
            if (start > 0) {
                it.seek(start)
            }
        }
    }

    private var position = start

    override fun toString(): String = "RandomAccessFileSource($fileObject)"
    override fun timeout(): Timeout = timeout

    @Throws(IOException::class)
    override fun read(sink: Buffer, byteCount: Long): Long {
        val channel = file.channel

        check(channel.isOpen) { "channel is closed" }
        if (position > endInclusive) return -1

        val bytesRequested = minOf(byteCount, endInclusive - position + 1)
        val rc = channel.transferTo(position, bytesRequested, sink)
        position += rc
        return rc
    }

    override fun close() {
        file.close()
    }
}
