/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.io.GzipByteReadChannel
import aws.smithy.kotlin.runtime.io.GzipSdkSource
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.*

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
                val compressed = gzipCompressBytes(sourceBytes)

                object : ByteStream.Buffer() {
                    override fun bytes(): ByteArray = compressed
                }
            }
        }
    }
}

internal fun gzipCompressBytes(bytes: ByteArray): ByteArray {
    val compressedBuffer = UByteArray(bytes.size + 128)
    memScoped {
        val zStream = alloc<z_stream>().apply {
            zalloc = null
            zfree = null
            opaque = null
        }

        // Initialize the deflate context with gzip encoding
        val initResult = deflateInit2_(
            strm = zStream.ptr,
            level = Z_BEST_COMPRESSION,
            method = Z_DEFLATED,
            windowBits = 31, // 15 (max window bits) + 16 (GZIP offset)
            memLevel = 9,
            strategy = Z_DEFAULT_STRATEGY,
            version = ZLIB_VERSION,
            stream_size = sizeOf<z_stream>().toInt()
        )
        if (initResult != Z_OK) {
            throw IllegalStateException("Failed to initialize zlib with error code $initResult")
        }

        try {
            zStream.next_in = bytes.refTo(0).getPointer(memScope).reinterpret()
            zStream.avail_in = bytes.size.toUInt()

            compressedBuffer.usePinned { pinnedBuffer ->
                zStream.next_out = pinnedBuffer.addressOf(0)
                zStream.avail_out = compressedBuffer.size.toUInt()

                val deflateResult = deflate(zStream.ptr, Z_FINISH)
                if (deflateResult != Z_STREAM_END) {
                    throw IllegalStateException("Compression failed with error code $deflateResult")
                }
            }

            val compressedSize = compressedBuffer.size.toUInt() - zStream.avail_out
            return compressedBuffer.copyOf(compressedSize.toInt()).toByteArray()
        } finally {
            deflateEnd(zStream.ptr)
        }
    }
}
