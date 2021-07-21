/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

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
         * Provides [ByteArray] to be consumed. This *MUST* be idempotent as the data may be
         * read multiple times.
         */
        abstract fun bytes(): ByteArray
    }

    /**
     * Variant of a [ByteStream] with a streaming payload that can only be consumed once.
     */
    abstract class OneShotStream : ByteStream() {
        /**
         * Provides [SdkByteReadChannel] to read from/consume. This function MUST be idempotent, implementations must
         * return the same channel every time (or a channel in the equivalent state).
         */
        abstract fun readFrom(): SdkByteReadChannel
    }

    /**
     * Variant of an [ByteStream] with a streaming payload that can be consumed multiple times.
     */
    abstract class ReplayableStream : ByteStream() {
        /**
         * Provides [SdkByteReadChannel] to read from/consume. Implementations MUST provide a fresh read channel
         * reset to the original state on each invocation of [newReader]. Consumers are allowed
         * to close the stream and ask for a new one.
         */
        abstract fun newReader(): SdkByteReadChannel
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

private suspend fun consumeStream(chan: SdkByteReadChannel): ByteArray {
    val bytes = chan.readRemaining()
    // readRemaining will read up to `limit` bytes (which is defaulted to Int.MAX_VALUE) or until
    // the stream is closed and no more bytes remain.
    // This is usually sufficient to consume the stream but technically that's not what it's doing.
    // Save us a painful debug session later in the very rare chance this were to occur...
    check(chan.isClosedForRead) { "failed to read all bytes from ByteStream, more data still expected" }
    return bytes
}

/**
 * Consume the [ByteStream] and pull the entire contents into memory as a [ByteArray].
 * Only do this if you are sure the contents fit in-memory as this will read the entire contents
 * of a streaming variant.
 */
suspend fun ByteStream.toByteArray(): ByteArray = when (val stream = this) {
    is ByteStream.Buffer -> stream.bytes()
    is ByteStream.OneShotStream -> consumeStream(stream.readFrom())
    is ByteStream.ReplayableStream -> consumeStream(stream.newReader())
}

suspend fun ByteStream.decodeToString(): String = toByteArray().decodeToString()

fun ByteStream.cancel() {
    when (val stream = this) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.OneShotStream -> stream.readFrom().cancel(null)
        is ByteStream.ReplayableStream -> stream.newReader().cancel(null)
    }
}
