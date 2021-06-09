/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.content

import software.aws.clientrt.io.SdkByteReadChannel

/**
 * Represents an abstract read-only stream of bytes
 */
sealed class ByteStream {

    /**
     * The content length if known
     */
    open val contentLength: Long? = null

    /**
     * Variant of a [ByteStream] with payload represented as an in-memory byte buffer.
     */
    abstract class Buffer : ByteStream() {
        /**
         * Provides [ByteArray] to be consumed
         */
        abstract fun bytes(): ByteArray
    }

    /**
     * Variant of an [ByteStream] with a streaming payload. Content is read from the given source
     */
    abstract class Reader : ByteStream() {
        /**
         * Provides [SdkByteReadChannel] to read from/consume
         */
        abstract fun readFrom(): SdkByteReadChannel
    }

    companion object {
        /**
         * Create a [ByteStream] from a [String]
         */
        fun fromString(str: String): ByteStream = StringContent(str)

        /**
         * Create a [ByteStream] from a [ByteArray]
         */
        fun fromBytes(bytes: ByteArray): ByteStream = ByteArrayContent(bytes)
    }
}

/**
 * Consume the [ByteStream] and pull the entire contents into memory as a [ByteArray].
 * Only do this if you are sure the contents fit in-memory as this will read the entire contents
 * of a streaming variant.
 */
suspend fun ByteStream.toByteArray(): ByteArray = when (val stream = this) {
    is ByteStream.Buffer -> stream.bytes()
    is ByteStream.Reader -> {
        val chan = stream.readFrom()
        val bytes = chan.readRemaining()
        // readRemaining will read up to `limit` bytes (which is defaulted to Int.MAX_VALUE) or until
        // the stream is closed and no more bytes remain.
        // This is usually sufficient to consume the stream but technically that's not what it's doing.
        // Save us a painful debug session later in the very rare chance this were to occur...
        check(chan.isClosedForRead) { "failed to read all bytes from ByteStream.Reader, more data still expected" }
        bytes
    }
}

suspend fun ByteStream.decodeToString(): String = toByteArray().decodeToString()

fun ByteStream.cancel() {
    when (val stream = this) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.Reader -> stream.readFrom().cancel(null)
    }
}
