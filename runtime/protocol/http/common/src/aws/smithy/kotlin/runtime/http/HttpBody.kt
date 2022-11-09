/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.readToBuffer
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * HTTP payload to be sent to a peer
 */
public sealed class HttpBody {
    /**
     * Specifies the length of this [HttpBody] content
     * If null it is assumed to be a streaming source using e.g. `Transfer-Encoding: Chunked`
     */
    public open val contentLength: Long? = null

    /**
     * Variant of a [HttpBody] without a payload
     */
    public object Empty : HttpBody() {
        override val contentLength: Long = 0
    }

    /**
     * Variant of a [HttpBody] with payload represented as [ByteArray]
     *
     * Useful for content that can be fully realized in memory (e.g. most text/JSON payloads)
     */
    public abstract class Bytes : HttpBody() {
        /**
         * Provides [ByteArray] to be sent to peer
         */
        public abstract fun bytes(): ByteArray
    }

    /**
     * Variant of an [HttpBody] with a streaming payload. Content is read from the given source
     */
    public abstract class Streaming : HttpBody() {
        /**
         * Provides [SdkByteReadChannel] for the content. Implementations MUST provide the same channel
         * every time [readFrom] is invoked until a call to [reset]. Replayable streams that support reset
         * MUST provide fresh channels after [reset] is invoked.
         */
        public abstract fun readFrom(): SdkByteReadChannel

        /**
         * Flag indicating the stream can be consumed multiple times. If `false` [reset] will throw an
         * [UnsupportedOperationException].
         */
        public open val isReplayable: Boolean = false

        /**
         * Flag indicating that this request body should be handled as a duplex stream by the underlying engine.
         *
         * Most request bodies are sent completely before the response is received. In full duplex mode the request
         * and response bodies may be interleaved. Only HTTP/2 calls support duplex streaming.
         */
        public open val isDuplex: Boolean = false

        /**
         * Reset the stream such that the next call to [readFrom] provides a fresh channel.
         * @throws UnsupportedOperationException if the stream can only be consumed once. Consumers can check
         * [isReplayable] before calling
         */
        public open fun reset() { throw UnsupportedOperationException("${this::class.simpleName} can only be consumed once") }
    }

    public companion object {
        /**
         * Create a [HttpBody] from a [ByteArray]
         */
        public fun fromBytes(bytes: ByteArray): HttpBody = ByteArrayContent(bytes)
    }
}

/**
 * Convert a [ByteArray] into an [HttpBody]
 */
public fun ByteArray.toHttpBody(): HttpBody = HttpBody.fromBytes(this)

/**
 * Convert a [String] into an [HttpBody]
 */
public fun String.toHttpBody(): HttpBody = encodeToByteArray().toHttpBody()

/**
 * Convert a [ByteStream] to the equivalent [HttpBody] variant
 */
public fun ByteStream.toHttpBody(): HttpBody = when (val byteStream = this) {
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
            channel?.cancel(null)
            channel = null
        }
    }
}

/**
 * Convert a [SdkByteReadChannel] to an [HttpBody]
 * @param contentLength the total content length of the channel if known
 */
@InternalApi
public fun SdkByteReadChannel.toHttpBody(contentLength: Long? = null): HttpBody {
    val ch = this
    return object : HttpBody.Streaming() {
        override val contentLength: Long? = contentLength
        override val isReplayable: Boolean = false
        override fun readFrom(): SdkByteReadChannel = ch
    }
}

// FIXME - replace/move to reading to SdkBuffer instead
/**
 * Consume the [HttpBody] and pull the entire contents into memory as a [ByteArray].
 * Only do this if you are sure the contents fit in-memory as this will read the entire contents
 * of a streaming variant.
 */
public suspend fun HttpBody.readAll(): ByteArray? = when (this) {
    is HttpBody.Empty -> null
    is HttpBody.Bytes -> this.bytes()
    is HttpBody.Streaming -> {
        val readChan = readFrom()
        val bytes = readChan.readToBuffer().readByteArray()
        bytes
    }
}

/**
 * Convert an [HttpBody] variant to the corresponding [ByteStream] variant or null if empty.
 */
public fun HttpBody.toByteStream(): ByteStream? = when (val body = this) {
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

/**
 * Convenience function to treat all [HttpBody] variants with a payload as an [SdkByteReadChannel]
 */
@InternalApi
public fun HttpBody.toSdkByteReadChannel(): SdkByteReadChannel? = when (val body = this) {
    is HttpBody.Empty -> null
    is HttpBody.Bytes -> SdkByteReadChannel(body.bytes())
    is HttpBody.Streaming -> body.readFrom()
}
