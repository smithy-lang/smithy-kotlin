/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.content

import software.aws.clientrt.io.Source

/**
 * Represents an abstract stream of bytes
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
         * Provides [Source] to read from/consume
         */
        abstract fun readFrom(): Source
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

suspend fun ByteStream.toByteArray(): ByteArray {
    return when (val stream = this) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.Reader -> stream.readFrom().readAll()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun ByteStream.decodeToString(): String = toByteArray().decodeToString()

fun ByteStream.cancel() {
    when (val stream = this) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.Reader -> stream.readFrom().cancel(null)
    }
}
