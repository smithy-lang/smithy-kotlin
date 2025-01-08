/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.internal.JobChannel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A source for reading a stream of bytes (e.g. from file, network, or in-memory buffer). Sources may
 * be layered to transform data as it is read (e.g. to decompress, decrypt, or remove protocol framing).
 *
 * Most application code should not operate on a source directly, but rather a [SdkBufferedSource] which is
 * more convenient. Use [SdkSource.buffer] to wrap any source with a buffer.
 *
 * ### Thread/Coroutine Safety
 *
 * Sources are not thread safe by default. Do not share a source between threads or coroutines without external
 * synchronization.
 *
 * This is a blocking interface! Use from coroutines should be done from an appropriate dispatcher
 * (e.g. `Dispatchers.IO`).
 */
public interface SdkSource : Closeable {
    /**
     * Remove at least 1 byte, and up-to [limit] bytes from this and appends them to [sink].
     * Returns the number of bytes read, or -1 if this source is exhausted.
     */
    @Throws(IOException::class)
    public fun read(sink: SdkBuffer, limit: Long): Long

    /**
     * Closes this source and releases any resources held. It is an error to read from a closed
     * source. This is an idempotent operation.
     */
    @Throws(IOException::class)
    override fun close()
}

/**
 * Consume the [SdkSource] and pull the entire contents into memory as a [ByteArray].
 */
@InternalApi
public suspend fun SdkSource.readToByteArray(): ByteArray = withContext(Dispatchers.IO) {
    use { it.buffer().readByteArray() }
}

/**
 * Convert the [SdkSource] to an [SdkByteReadChannel]. Content is read from the source and forwarded
 * to the channel.
 * @param coroutineScope the coroutine scope to use to launch a background reader channel responsible for propagating data
 * between source and the returned channel
 */
@OptIn(DelicateCoroutinesApi::class)
@InternalApi
public fun SdkSource.toSdkByteReadChannel(coroutineScope: CoroutineScope? = null): SdkByteReadChannel {
    val source = this
    val ch = JobChannel()
    val scope = coroutineScope ?: GlobalScope
    val job = scope.launch(Dispatchers.IO + CoroutineName("sdk-source-reader")) {
        val buffer = SdkBuffer()
        val result = runCatching {
            source.use {
                while (true) {
                    ensureActive()
                    val rc = source.read(buffer, DEFAULT_BYTE_CHANNEL_MAX_BUFFER_SIZE.toLong())
                    if (rc == -1L) break
                    ch.write(buffer)
                }
            }
        }

        ch.close(result.exceptionOrNull())
    }

    ch.attachJob(job)

    return ch
}

/**
 * Remove exactly [byteCount] bytes from this source and appends them to [sink].
 * @param sink The sink to append bytes to
 * @param byteCount the number of bytes to read from the source
 * @throws [IllegalArgumentException] when [byteCount] is less than zero
 * @throws [EOFException] when the source is exhausted before [byteCount] bytes could be read
 */
@Throws(IOException::class)
public fun SdkSource.readFully(sink: SdkBuffer, byteCount: Long) {
    require(byteCount >= 0L) { "Invalid length ($byteCount) must be >= 0L" }

    var totalBytesRead = 0L
    while (totalBytesRead != byteCount) {
        val rc = read(sink, byteCount - totalBytesRead)

        if (rc == -1L) {
            throw EOFException("Unexpected EOF: expected ${byteCount - totalBytesRead} more bytes; consumed: $totalBytesRead")
        }

        totalBytesRead += rc
    }
}
