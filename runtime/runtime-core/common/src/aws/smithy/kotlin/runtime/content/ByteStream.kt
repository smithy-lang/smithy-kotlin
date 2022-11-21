/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
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
         * Implementations that are replayable ([isOneShot] = `true`) MUST provide a fresh read channel
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
         * Implementations that are replayable ([isOneShot] = `true`) MUST provide a fresh source
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
    is ByteStream.SourceStream -> consumeSourceAsByteArray(stream.readFrom())
}

public suspend fun ByteStream.decodeToString(): String = toByteArray().decodeToString()

public fun ByteStream.cancel() {
    when (val stream = this) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.ChannelStream -> stream.readFrom().cancel(null)
        is ByteStream.SourceStream -> stream.readFrom().close()
    }
}

internal expect suspend fun consumeSourceAsByteArray(source: SdkSource): ByteArray
