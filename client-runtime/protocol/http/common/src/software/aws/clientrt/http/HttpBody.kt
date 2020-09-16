/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import software.aws.clientrt.content.ByteStream
import software.aws.clientrt.io.Source

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
        override val contentLength: Long? = 0
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
         * Provides [Source] for the content
         */
        abstract fun readFrom(): Source
    }
}

/**
 * Convert a [ByteStream] to the equivalent [HttpBody] variant
 */
fun ByteStream.toHttpBody(): HttpBody {
    return when (val bytestream = this) {
        is ByteStream.Buffer -> object : HttpBody.Bytes() {
            override val contentLength: Long? = bytestream.contentLength
            override fun bytes(): ByteArray = bytestream.bytes()
        }
        is ByteStream.Reader -> object : HttpBody.Streaming() {
            override val contentLength: Long? = bytestream.contentLength
            override fun readFrom(): Source = bytestream.readFrom()
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
    is HttpBody.Streaming -> this.readFrom().readAll()
}

/**
 * Convert an [HttpBody] variant to the corresponding [ByteStream] variant or null if empty.
 */
fun HttpBody.toByteStream(): ByteStream? {
    return when (val body = this) {
        is HttpBody.Empty -> null
        is HttpBody.Bytes -> object : ByteStream.Buffer() {
            override val contentLength: Long? = body.contentLength
            override fun bytes(): ByteArray = body.bytes()
        }
        is HttpBody.Streaming -> object : ByteStream.Reader() {
            override val contentLength: Long? = body.contentLength
            override fun readFrom(): Source = body.readFrom()
        }
    }
}
