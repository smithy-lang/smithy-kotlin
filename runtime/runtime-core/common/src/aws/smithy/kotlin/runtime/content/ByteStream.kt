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
     * Variant of an [ByteStream] with a streaming payload. Content is read from the given source
     */
    abstract class Reader : ByteStream() {
        /**
         * Provides [SdkByteReadChannel] to read from/consume. Implementations that are idempotent *MUST* provide
         * a fresh read channel reset to the original state on each invocation of [readFrom]. Consumers are allowed
         * to close the stream and ask for a new one when it is marked as idempotent.
         */
        abstract fun readFrom(): SdkByteReadChannel

        /**
         * Flag indicating that [readFrom] is an idempotent operation and that the channel to read from can be
         * created multiple times. A stream that is non-idempotent can only be consumed once.
         *
         * As an example, implementations backed by files or in-memory buffers should be idempotent.
         * A stream that is produced dynamically where the original data cannot be re-constructed can only be read
         * from once and are considered non-idempotent.
         *
         * Idempotency is an important aspect for operations that require (e.g.) calculating checksums on the data
         * provided.
         */
        open val isIdempotent: Boolean = false
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
