/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.readToBuffer

/**
 * Represents an abstract read-only stream of bytes
 */
public sealed class ByteStream {

    /**
     * The content length if known
     */
    public open val contentLength: Long? = null

    /**
     * Variant of a [ByteStream] with payload represented as an in-memory byte buffer.
     */
    public abstract class Buffer : ByteStream() {
        /**
         * Provides [ByteArray] to be consumed. This *MUST* be idempotent as the data may be
         * read multiple times.
         */
        public abstract fun bytes(): ByteArray
    }

    /**
     * Variant of a [ByteStream] with a streaming payload that can only be consumed once.
     */
    public abstract class OneShotStream : ByteStream() {
        /**
         * Provides [SdkByteReadChannel] to read from/consume. This function MUST be idempotent, implementations must
         * return the same channel every time (or a channel in the equivalent state).
         */
        public abstract fun readFrom(): SdkByteReadChannel
    }

    /**
     * Variant of an [ByteStream] with a streaming payload that can be consumed multiple times.
     */
    public abstract class ReplayableStream : ByteStream() {
        /**
         * Provides [SdkByteReadChannel] to read from/consume. Implementations MUST provide a fresh read channel
         * reset to the original state on each invocation of [newReader]. Consumers are allowed
         * to close the stream and ask for a new one.
         */
        public abstract fun newReader(): SdkByteReadChannel
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

private suspend fun consumeStream(chan: SdkByteReadChannel): ByteArray {
    val bytes = chan.readToBuffer().readByteArray()
    check(chan.isClosedForRead) { "failed to read all bytes from ByteStream, more data still expected" }
    return bytes
}

/**
 * Consume the [ByteStream] and pull the entire contents into memory as a [ByteArray].
 * Only do this if you are sure the contents fit in-memory as this will read the entire contents
 * of a streaming variant.
 */
public suspend fun ByteStream.toByteArray(): ByteArray = when (val stream = this) {
    is ByteStream.Buffer -> stream.bytes()
    is ByteStream.OneShotStream -> consumeStream(stream.readFrom())
    is ByteStream.ReplayableStream -> consumeStream(stream.newReader())
}

public suspend fun ByteStream.decodeToString(): String = toByteArray().decodeToString()

public fun ByteStream.cancel() {
    when (val stream = this) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.OneShotStream -> stream.readFrom().cancel(null)
        is ByteStream.ReplayableStream -> stream.newReader().cancel(null)
    }
}
