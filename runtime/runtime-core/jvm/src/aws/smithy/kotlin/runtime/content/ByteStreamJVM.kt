/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
    require(endInclusive >= start - 1) {
        "end index $endInclusive must be greater than or equal to start index minus one (${start - 1})"
    }

    val len = length()
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

    val calculatedEndInclusive = if (endInclusive == -1L) f.length() - 1L else endInclusive
    require(calculatedEndInclusive >= start - 1) { "end index $calculatedEndInclusive must be greater or equal to start index minus one (${start - 1})" }

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

/**
 * Create a blocking [InputStream] that reads from the underlying [ByteStream].
 */
public fun ByteStream.toInputStream(): InputStream = when (this) {
    is ByteStream.Buffer -> ByteArrayInputStream(bytes())
    is ByteStream.ChannelStream -> readFrom().toInputStream()
    is ByteStream.SourceStream -> readFrom().buffer().inputStream()
}

/**
 * Create a [ByteStream.SourceStream] that reads from the given [InputStream]
 * @param inputStream The [InputStream] from which to create a [ByteStream]
 * @param contentLength If specified, indicates how many bytes remain in the input stream. Defaults to `null`.
 */
public fun ByteStream.Companion.fromInputStream(
    inputStream: InputStream,
    contentLength: Long? = null,
): ByteStream.SourceStream = inputStream.asByteStream(contentLength)

/**
 * Create a [ByteStream.SourceStream] that reads from this [InputStream]
 * @param contentLength If specified, indicates how many bytes remain in this stream. Defaults to `null`.
 */
public fun InputStream.asByteStream(contentLength: Long? = null): ByteStream.SourceStream {
    if (markSupported() && contentLength != null) {
        mark(contentLength.toInt())
    }

    return object : ByteStream.SourceStream() {
        override val contentLength: Long? = contentLength
        override val isOneShot: Boolean = !markSupported()
        override fun readFrom(): SdkSource {
            if (markSupported() && contentLength != null) {
                reset()
                mark(contentLength.toInt())
                return object : SdkSource by source() {
                    /*
                     * This is a no-op close to prevent body hashing from closing the underlying InputStream, which causes
                     * `IOException: Stream closed` on subsequent reads. Consider making [ByteStream.ChannelStream]/[ByteStream.SourceStream]
                     * (or possibly even [ByteStream] itself) implement [Closeable] to better handle closing streams.
                     * This should allow us to clean up our usage of [ByteStream.cancel()].
                     */
                    override fun close() { }
                }
            }

            return source()
        }
    }
}

/**
 * Writes this stream to the given [OutputStream], then closes it.
 * @param outputStream The [OutputStream] to which the contents of this stream will be written
 */
public suspend fun ByteStream.writeToOutputStream(outputStream: OutputStream): Long = withContext(Dispatchers.IO) {
    val src = when (val stream = this@writeToOutputStream) {
        is ByteStream.ChannelStream -> return@withContext outputStream.writeAll(stream.readFrom())
        is ByteStream.Buffer -> stream.bytes().source()
        is ByteStream.SourceStream -> stream.readFrom()
    }

    outputStream.sink().use {
        it.buffer().use { bufferedSink ->
            bufferedSink.writeAll(src)
        }
    }
}

/**
 * Writes this stream to the given [OutputStream]. This method does not flush or close the given [OutputStream].
 * @param outputStream The [OutputStream] to which the contents of this stream will be written
 */
public suspend fun ByteStream.appendToOutputStream(outputStream: OutputStream): Long = withContext(Dispatchers.IO) {
    val src = when (val stream = this@appendToOutputStream) {
        is ByteStream.ChannelStream -> return@withContext outputStream.writeAll(stream.readFrom())
        is ByteStream.Buffer -> stream.bytes().source()
        is ByteStream.SourceStream -> stream.readFrom()
    }

    val out = outputStream.sink().buffer()
    out.writeAll(src)
}

private suspend fun OutputStream.writeAll(chan: SdkByteReadChannel): Long =
    sink().use {
        chan.readAll(it)
    }
