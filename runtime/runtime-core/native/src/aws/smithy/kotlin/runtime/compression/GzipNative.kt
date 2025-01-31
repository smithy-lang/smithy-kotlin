/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.sdk.kotlin.crt.use
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.io.GzipByteReadChannel
import aws.smithy.kotlin.runtime.io.GzipSdkSource
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import kotlinx.coroutines.runBlocking

/**
 * The gzip compression algorithm.
 * Used to compress http requests.
 *
 * See: https://en.wikipedia.org/wiki/Gzip
 */
public actual class Gzip : CompressionAlgorithm {
    actual override val id: String = "gzip"
    actual override val contentEncoding: String = "gzip"

    actual override fun compress(stream: ByteStream): ByteStream = when (stream) {
        is ByteStream.ChannelStream -> object : ByteStream.ChannelStream() {
            override fun readFrom(): SdkByteReadChannel = GzipByteReadChannel(stream.readFrom())
            override val contentLength: Long? = null
            override val isOneShot: Boolean = stream.isOneShot
        }

        is ByteStream.SourceStream -> object : ByteStream.SourceStream() {
            override fun readFrom(): SdkSource = GzipSdkSource(stream.readFrom())
            override val contentLength: Long? = null
            override val isOneShot: Boolean = stream.isOneShot
        }

        is ByteStream.Buffer -> {
            val sourceBytes = stream.bytes()
            if (sourceBytes.isEmpty()) {
                stream
            } else {
                val compressed = runBlocking {
                    GzipCompressor().use {
                        it.apply {
                            update(sourceBytes)
                        }.flush()
                    }
                }

                ByteStream.fromBytes(compressed)
            }
        }
    }
}
