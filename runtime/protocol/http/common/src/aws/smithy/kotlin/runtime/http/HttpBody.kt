/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

/**
 * HTTP payload to be sent to a peer
 */
sealed class HttpBody {

    /**
     * Specifies the length of this [HttpBody] content
     * If null it is assumed to be a streaming source using e.g. `Transfer-Encoding: Chunked`
     */
    open val contentLength: Long? = null

    /**
     * Variant of a [HttpBody] without a payload
     */
    object Empty : HttpBody() {
        override val contentLength: Long = 0
    }

    /**
     * Variant of a [HttpBody] with payload represented as [ByteArray]
     *
     * Useful for content that can be fully realized in memory (e.g. most text/JSON payloads)
     */
    abstract class Bytes : HttpBody() {
        /**
         * Provides [ByteArray] to be sent to peer
         */
        abstract fun bytes(): ByteArray
    }

    /**
     * Variant of an [HttpBody] with a streaming payload. Content is read from the given source
     */
    abstract class Streaming : HttpBody() {
        /**
         * Provides [SdkByteReadChannel] for the content. Implementations MUST provide the same channel
         * every time [readFrom] is invoked until a call to [reset]. Replayable streams that support reset
         * MUST provide fresh channels after [reset] is invoked.
         */
        abstract fun readFrom(): SdkByteReadChannel

        /**
         * Flag indicating the stream can be consumed multiple times. If `false` [reset] will throw an
         * [UnsupportedOperationException].
         */
        open val isReplayable: Boolean = false

        /**
         * Reset the stream such that the next call to [readFrom] provides a fresh channel.
         * @throws UnsupportedOperationException if the stream can only be consumed once. Consumers can check
         * [isReplayable] before calling
         */
        open fun reset() { throw UnsupportedOperationException("${this::class.simpleName} can only be consumed once") }
    }
}

/**
 * Convert a [ByteStream] to the equivalent [HttpBody] variant
 */
fun ByteStream.toHttpBody(): HttpBody = when (val byteStream = this) {
    is ByteStream.Buffer -> object : HttpBody.Bytes() {
        override val contentLength: Long? = byteStream.contentLength
        override fun bytes(): ByteArray = byteStream.bytes()
    }
    is ByteStream.OneShotStream -> object : HttpBody.Streaming() {
        override val contentLength: Long? = byteStream.contentLength
        override fun readFrom(): SdkByteReadChannel = byteStream.readFrom()
    }
    is ByteStream.ReplayableStream -> object : HttpBody.Streaming() {
        private var channel: SdkByteReadChannel? = null
        override val contentLength: Long? = byteStream.contentLength
        override fun readFrom(): SdkByteReadChannel = channel ?: byteStream.newReader().also { channel = it }
        override val isReplayable: Boolean = true
        override fun reset() {
            channel?.close()
            channel = null
        }
    }
}

/**
 * Consume the [HttpBody] and pull the entire contents into memory as a [ByteArray].
 * Only do this if you are sure the contents fit in-memory as this will read the entire contents
 * of a streaming variant.
 */
suspend fun HttpBody.readAll(): ByteArray? = when (this) {
    is HttpBody.Empty -> null
    is HttpBody.Bytes -> this.bytes()
    is HttpBody.Streaming -> {
        val readChan = readFrom()
        val bytes = readChan.readRemaining()
        // readRemaining will read up to `limit` bytes (which is defaulted to Int.MAX_VALUE) or until
        // the stream is closed and no more bytes remain.
        // This is usually sufficient to consume the stream but technically that's not what it's doing.
        // Save us a painful debug session later in the very rare chance this were to occur.
        check(readChan.isClosedForRead) { "failed to read all HttpBody bytes from stream" }
        bytes
    }
}

/**
 * Convert an [HttpBody] variant to the corresponding [ByteStream] variant or null if empty.
 */
fun HttpBody.toByteStream(): ByteStream? = when (val body = this) {
    is HttpBody.Empty -> null
    is HttpBody.Bytes -> object : ByteStream.Buffer() {
        override val contentLength: Long? = body.contentLength
        override fun bytes(): ByteArray = body.bytes()
    }
    is HttpBody.Streaming -> object : ByteStream.OneShotStream() {
        override val contentLength: Long? = body.contentLength
        override fun readFrom(): SdkByteReadChannel = body.readFrom()
    }
}
