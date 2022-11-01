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
    file: File,
    start: Long,
    private val endInclusive: Long,
    private val timeout: Timeout = Timeout.NONE,
) : okio.Source {
    private val file = RandomAccessFile(file, "r")

    init {
        require(start >= 0L) { "start position should be >= 0, found $start" }
        require(endInclusive >= 0 && endInclusive <= file.length() - 1) {
            "endInclusive should be less than or equal to the length of the file, was $endInclusive"
        }
        if (start > 0) {
            this.file.seek(start)
        }
    }

    private var position = start
    private val channel = this.file.channel

    override fun toString(): String = "RandomAccessFileSource($file)"
    override fun timeout(): Timeout = timeout

    @Throws(IOException::class)
    override fun read(sink: Buffer, byteCount: Long): Long {
        check(channel.isOpen) { "channel is closed" }
        if (position > endInclusive) return -1

        val bytesRequested = minOf(byteCount, endInclusive - position + 1)
        val rc = channel.transferTo(position, bytesRequested, sink)
        position += rc
        return rc
    }

    override fun close() {
        channel.close()
        file.close()
    }
}
