/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.io.*
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
     * Flag indicating the body can be consumed only once
     */
    public open val isOneShot: Boolean = true

    /**
     * Flag indicating that this request body should be handled as a duplex stream by the underlying engine.
     *
     * Most request bodies are sent completely before the response is received. In full duplex mode the request
     * and response bodies may be interleaved. Only HTTP/2 calls support duplex streaming.
     */
    public open val isDuplex: Boolean = false

    /**
     * Variant of a [HttpBody] without a payload
     */
    public object Empty : HttpBody() {
        final override val isOneShot: Boolean = false
        override val contentLength: Long = 0
    }

    /**
     * Variant of a [HttpBody] with payload represented as [ByteArray]
     *
     * Useful for content that can be fully realized in memory (e.g. most text/JSON payloads)
     */
    public abstract class Bytes : HttpBody() {
        // implementations MUST be idempotent and replayable or else they should be modeled differently
        // this is meant for simple in-memory representations only
        final override val isOneShot: Boolean = false

        /**
         * Provides [ByteArray] to be sent to peer
         */
        public abstract fun bytes(): ByteArray
    }

    /**
     * Variant of an [HttpBody] with a streaming payload read from an [SdkSource]
     */
    public abstract class SourceContent : HttpBody() {
        /**
         * Provides [SdkSource] that will be sent to peer. Replayable streams ([HttpBody.isOneShot] = `false`)
         * MUST provide a fresh channel every time [readFrom] is invoked.
         */
        public abstract fun readFrom(): SdkSource
    }

    /**
     * Variant of an [HttpBody] with a streaming payload read from an [SdkByteReadChannel]
     */
    public abstract class ChannelContent : HttpBody() {
        /**
         * Provides [SdkByteReadChannel] that will be sent to peer. Replayable streams ([HttpBody.isOneShot] = `false`)
         * MUST provide a fresh channel every time [readFrom] is invoked.
         */
        public abstract fun readFrom(): SdkByteReadChannel
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
@InternalApi
public fun ByteStream.toHttpBody(): HttpBody = when (val byteStream = this) {
    is ByteStream.Buffer -> object : HttpBody.Bytes() {
        override val contentLength: Long? = byteStream.contentLength
        override fun bytes(): ByteArray = byteStream.bytes()
    }

    is ByteStream.ChannelStream -> object : HttpBody.ChannelContent() {
        override val contentLength: Long? = byteStream.contentLength
        override val isOneShot: Boolean = byteStream.isOneShot
        override fun readFrom(): SdkByteReadChannel = byteStream.readFrom()
    }
    is ByteStream.SourceStream -> object : HttpBody.SourceContent() {
        override val contentLength: Long? = byteStream.contentLength
        override val isOneShot: Boolean = byteStream.isOneShot
        override fun readFrom(): SdkSource = byteStream.readFrom()
    }
}

/**
 * Convert a [SdkByteReadChannel] to an [HttpBody]
 * @param contentLength the total content length of the channel if known
 */
@InternalApi
public fun SdkByteReadChannel.toHttpBody(contentLength: Long? = null): HttpBody {
    val ch = this
    return object : HttpBody.ChannelContent() {
        override val contentLength: Long? = contentLength
        override val isOneShot: Boolean = true
        override fun readFrom(): SdkByteReadChannel = ch
    }
}

/**
 * Convert an [SdkSource] to an [HttpBody]
 * @param contentLength the total content length of the source, if known
 */
@InternalApi
public fun SdkSource.toHttpBody(contentLength: Long? = null): HttpBody =
    object : HttpBody.SourceContent() {
        override val contentLength: Long? = contentLength
        override val isOneShot: Boolean = true
        override fun readFrom(): SdkSource = this@toHttpBody
    }

/**
 * Convert a [HttpBody.SourceContent] or [HttpBody.ChannelContent] to a body with a [HashingSource] or [HashingByteReadChannel], respectively.
 * @param hashFunction the hash function to wrap the body with
 * @param contentLength the total content length of the source, if known
 */
@InternalApi
public fun HttpBody.toHashingBody(hashFunction: HashFunction, contentLength: Long?): HttpBody = when (this) {
    is HttpBody.SourceContent ->
        HashingSource(
            hashFunction,
            readFrom(),
        ).toHttpBody(contentLength)
    is HttpBody.ChannelContent -> HashingByteReadChannel(
        hashFunction,
        readFrom(),
    ).toHttpBody(contentLength)
    else -> throw ClientException("HttpBody type is not supported")
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
    is HttpBody.ChannelContent -> {
        val readChan = readFrom()
        val bytes = readChan.readToBuffer().readByteArray()
        bytes
    }

    is HttpBody.SourceContent -> readFrom().readToByteArray()
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
    is HttpBody.ChannelContent -> object : ByteStream.ChannelStream() {
        override val contentLength: Long? = body.contentLength
        override val isOneShot: Boolean = body.isOneShot
        override fun readFrom(): SdkByteReadChannel = body.readFrom()
    }
    is HttpBody.SourceContent -> object : ByteStream.SourceStream() {
        override val contentLength: Long? = body.contentLength
        override val isOneShot: Boolean = body.isOneShot
        override fun readFrom(): SdkSource = body.readFrom()
    }
}

/**
 * Convenience function to treat all [HttpBody] variants with a payload as an [SdkByteReadChannel]
 */
@InternalApi
public fun HttpBody.toSdkByteReadChannel(): SdkByteReadChannel? = when (val body = this) {
    is HttpBody.Empty -> null
    is HttpBody.Bytes -> SdkByteReadChannel(body.bytes())
    is HttpBody.ChannelContent -> body.readFrom()
    is HttpBody.SourceContent -> body.readFrom().toSdkByteReadChannel()
}
