/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Represents an abstract read-only stream of bytes
 */
public sealed class ByteStream {

    /**
     * The content length if known
     */
    public open val contentLength: Long? = null

    /**
     * Flag indicating if the body can only be consumed once. If false the underlying stream
     * must be capable of being replayed.
     */
    public open val isOneShot: Boolean = true

    /**
     * Variant of a [ByteStream] with payload represented as an in-memory byte buffer.
     */
    public abstract class Buffer : ByteStream() {
        // implementations MUST be idempotent and replayable or else they should be modeled differently
        // this is meant for simple in-memory representations only
        final override val isOneShot: Boolean = false

        /**
         * Provides [ByteArray] to be consumed. This *MUST* be idempotent as the data may be
         * read multiple times.
         */
        public abstract fun bytes(): ByteArray
    }

    /**
     * Variant of a [ByteStream] with a streaming payload read from an [SdkByteReadChannel]
     */
    public abstract class ChannelStream : ByteStream() {
        /**
         * Provides [SdkByteReadChannel] to read from/consume.
         *
         * Implementations that are replayable ([isOneShot] = `false`) MUST provide a fresh read channel
         * reset to the original state on each invocation of [readFrom]. Consumers are allowed
         * to close the stream and ask for a new one.
         */
        public abstract fun readFrom(): SdkByteReadChannel
    }

    /**
     * Variant of a [ByteStream] with a streaming payload read from an [SdkSource]
     */
    public abstract class SourceStream : ByteStream() {
        /**
         * Provides [SdkSource] to read from/consume.
         *
         * Implementations that are replayable ([isOneShot] = `false`) MUST provide a fresh source
         * reset to the original state on each invocation of [readFrom]. Consumers are allowed
         * to close the stream and ask for a new one.
         */
        public abstract fun readFrom(): SdkSource
    }

    public companion object {
        /**
         * Create a [ByteStream] from a [String]
         */
        public fun fromString(str: String): ByteStream = StringContent(str)

        /**
         * Create a [ByteStream] from a [ByteArray]
         */
        public fun fromBytes(bytes: ByteArray): ByteStream = ByteArrayContent(bytes)
    }
}

/**
 * Consume the [ByteStream] and pull the entire contents into memory as a [ByteArray].
 * Only do this if you are sure the contents fit in-memory as this will read the entire contents
 * of a streaming variant.
 */
public suspend fun ByteStream.toByteArray(): ByteArray = when (val stream = this) {
    is ByteStream.Buffer -> stream.bytes()
    is ByteStream.ChannelStream -> {
        val chan = stream.readFrom()
        val bytes = chan.readToBuffer().readByteArray()
        check(chan.isClosedForRead) { "failed to read all bytes from ByteStream, more data still expected" }
        bytes
    }
    is ByteStream.SourceStream -> stream.readFrom().readToByteArray()
}

public suspend fun ByteStream.decodeToString(): String = toByteArray().decodeToString()

public fun ByteStream.cancel() {
    when (val stream = this) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.ChannelStream -> stream.readFrom().cancel(null)
        is ByteStream.SourceStream -> stream.readFrom().close()
    }
}

/**
 * Return a [Flow] that consumes the underlying [ByteStream] when collected.
 *
 * @param bufferSizeHint the size of the buffers to emit from the flow. All buffers emitted
 * will be of this size except for the last one which may be less than the requested buffer size.
 * This parameter has no effect for the [ByteStream.Buffer] variant. The emitted [ByteArray]
 * will be whatever size the in-memory buffer already is in that case.
 */
public fun ByteStream.toFlow(bufferSizeHint: Long = 8192): Flow<ByteArray> = when (this) {
    is ByteStream.Buffer -> flowOf(bytes())
    is ByteStream.ChannelStream -> readFrom().toFlow(bufferSizeHint)
    is ByteStream.SourceStream -> readFrom().toFlow(bufferSizeHint).flowOn(SdkDispatchers.IO)
}

/**
 * Create a [ByteStream] from a [Flow] of individual [ByteArray]'s.
 *
 * @param scope the [CoroutineScope] to use for launching a coroutine to do the collection in.
 * @param contentLength the overall content length of the [Flow] (if known). If set this will be
 * used as [ByteStream.contentLength]. Some APIs require a known `Content-Length` header and
 * since the total size of the flow can't be calculated without collecting it callers should set this
 * parameter appropriately in those cases.
 */
public fun Flow<ByteArray>.toByteStream(
    scope: CoroutineScope,
    contentLength: Long? = null,
): ByteStream {
    val ch = SdkByteChannel(true)
    var totalWritten = 0L
    val job = scope.launch {
        collect { bytes ->
            ch.write(bytes)
            totalWritten += bytes.size

            check(contentLength == null || totalWritten <= contentLength) {
                "$totalWritten bytes collected from flow exceeds reported content length of $contentLength"
            }
        }

        check(contentLength == null || totalWritten == contentLength) {
            "expected $contentLength bytes collected from flow, got $totalWritten"
        }

        ch.close()
    }

    job.invokeOnCompletion { cause ->
        ch.close(cause)
    }

    return object : ByteStream.ChannelStream() {
        override val contentLength: Long? = contentLength
        override val isOneShot: Boolean = true
        override fun readFrom(): SdkByteReadChannel = ch
    }
}

private fun SdkByteReadChannel.toFlow(bufferSize: Long): Flow<ByteArray> = flow {
    val chan = this@toFlow
    val sink = SdkBuffer()
    while (!chan.isClosedForRead) {
        val rc = chan.read(sink, bufferSize)
        if (rc == -1L) break
        if (sink.size >= bufferSize) {
            val bytes = sink.readByteArray(bufferSize)
            emit(bytes)
        }
    }
    if (sink.size > 0L) {
        emit(sink.readByteArray())
    }
}

private fun SdkSource.toFlow(bufferSize: Long): Flow<ByteArray> = flow {
    val source = this@toFlow
    val sink = SdkBuffer()
    while (true) {
        val rc = source.read(sink, bufferSize)
        if (rc == -1L) break
        if (sink.size >= bufferSize) {
            val bytes = sink.readByteArray(bufferSize)
            emit(bytes)
        }
    }
    if (sink.size > 0L) {
        emit(sink.readByteArray())
    }
}
