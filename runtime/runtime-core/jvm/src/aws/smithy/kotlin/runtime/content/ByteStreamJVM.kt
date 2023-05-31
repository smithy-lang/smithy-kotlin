/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import kotlin.io.use

// JVM specific extensions for dealing with ByteStream's

/**
 * Create a [ByteStream] from a file
 */
public fun ByteStream.Companion.fromFile(file: File): ByteStream = file.asByteStream()

/**
 * Create a [ByteStream] from a file
 */
public fun File.asByteStream(start: Long = 0, endInclusive: Long = length() - 1): ByteStream {
    require(start >= 0) { "start index $start cannot be negative" }
    val len = length()
    if (len != 0L) {
        require(endInclusive >= start) {
            "end index $endInclusive must be greater than or equal to start index $start"
        }
    }
    require(endInclusive < len) { "end index $endInclusive must be less than file size $len" }

    return FileContent(this, start, endInclusive)
}

/**
 * Create a [ByteStream] from a file with the given range
 */
public fun File.asByteStream(range: LongRange): ByteStream = asByteStream(range.first, range.last)

/**
 * Create a [ByteStream] from a path
 */
public fun Path.asByteStream(start: Long = 0, endInclusive: Long = -1): ByteStream {
    val f = toFile()
    require(f.exists()) { "cannot create ByteStream, file does not exist: $this" }
    require(f.isFile) { "cannot create a ByteStream from a directory: $this" }
    if (f.length() != 0L) {
        require(endInclusive >= -1L) { "endInclusive must be -1 or greater" }
    }

    val calculatedEndInclusive = if (endInclusive == -1L) f.length() - 1L else endInclusive
    return f.asByteStream(start, calculatedEndInclusive)
}

/**
 * Create a [ByteStream] from a path with the given range
 */
public fun Path.asByteStream(range: LongRange): ByteStream = asByteStream(range.first, range.last)

/**
 * Write the contents of this ByteStream to file and close it
 * @return the number of bytes written
 */
public suspend fun ByteStream.writeToFile(file: File): Long = withContext(Dispatchers.IO) {
    val src = when (val stream = this@writeToFile) {
        is ByteStream.ChannelStream -> return@withContext file.writeAll(stream.readFrom())
        is ByteStream.Buffer -> stream.bytes().source()
        is ByteStream.SourceStream -> stream.readFrom()
    }

    file.sink().use {
        it.buffer().use { bufferedSink ->
            bufferedSink.writeAll(src)
        }
    }
}

private suspend fun File.writeAll(chan: SdkByteReadChannel): Long =
    sink().use {
        chan.readAll(it)
    }

/**
 * Write the contents of this ByteStream to file at the given path
 * @return the number of bytes written
 */
public suspend fun ByteStream.writeToFile(path: Path): Long = writeToFile(path.toFile())
