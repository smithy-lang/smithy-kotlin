/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.sdk.kotlin.crt.Closeable
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.readFully
import aws.smithy.kotlin.runtime.io.readToByteArray
import aws.smithy.kotlin.runtime.io.use
import aws.smithy.kotlin.runtime.io.write
import kotlinx.cinterop.*
import platform.zlib.*

private const val DEFAULT_WINDOW_BITS = 15 // Default window bits
private const val WINDOW_BITS_GZIP_OFFSET = 16 // Gzip offset for window bits
private const val MEM_LEVEL = 8 // Default memory level

/**
 * Streaming-style gzip compressor, implemented using zlib bindings
 */
internal class GzipCompressor : Closeable {
    companion object {
        internal const val BUFFER_SIZE = 16384
    }

    private val stream = nativeHeap.alloc<z_stream>()
    private val outputBuffer = SdkByteChannel()
    internal var isClosed = false

    internal val availableForRead: Int
        get() = outputBuffer.availableForRead

    init {
        // Initialize deflate with gzip encoding
        val initResult = deflateInit2_(
            stream.ptr,
            Z_BEST_COMPRESSION,
            Z_DEFLATED,
            DEFAULT_WINDOW_BITS + WINDOW_BITS_GZIP_OFFSET,
            MEM_LEVEL,
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            sizeOf<z_stream>().toInt(),
        )

        check(initResult == Z_OK) { "Failed to initialize zlib deflate with error code $initResult: ${zError(initResult)!!.toKString()}" }
    }

    /**
     * Update the compressor with [input] bytes
     */
    suspend fun update(input: ByteArray) = memScoped {
        check(!isClosed) { "Compressor is closed" }

        val inputPin = input.pin()

        stream.next_in = inputPin.addressOf(0).reinterpret()
        stream.avail_in = input.size.toUInt()

        val compressionBuffer = ByteArray(BUFFER_SIZE)

        while (stream.avail_in > 0u) {
            val outputPin = compressionBuffer.pin()
            stream.next_out = outputPin.addressOf(0).reinterpret()
            stream.avail_out = BUFFER_SIZE.toUInt()

            val deflateResult = deflate(stream.ptr, Z_NO_FLUSH)
            check(deflateResult == Z_OK) { "Deflate failed with error code $deflateResult" }

            val bytesWritten = BUFFER_SIZE - stream.avail_out.toInt()
            outputBuffer.write(compressionBuffer, 0, bytesWritten)

            outputPin.unpin()
        }

        inputPin.unpin()
    }

    /**
     * Consume [count] gzip-compressed bytes.
     */
    suspend fun consume(count: Int): ByteArray {
        check(!isClosed) { "Compressor is closed" }
        require(count in 0..availableForRead) {
            "Count must be between 0 and $availableForRead, got $count"
        }

        return SdkBuffer().use {
            outputBuffer.readFully(it, count.toLong())
            it.readToByteArray()
        }
    }

    /**
     * Flush the compressor and return the terminal sequence of bytes that represent the end of the gzip compression.
     */
    suspend fun flush(): ByteArray {
        check(!isClosed) { "Compressor is closed" }

        memScoped {
            val compressionBuffer = ByteArray(BUFFER_SIZE)
            var deflateResult: Int? = null
            var outputLength = 0L

            do {
                val outputPin = compressionBuffer.pin()
                stream.next_out = outputPin.addressOf(0).reinterpret()
                stream.avail_out = BUFFER_SIZE.toUInt()

                deflateResult = deflate(stream.ptr, Z_FINISH)
                check(deflateResult == Z_OK || deflateResult == Z_STREAM_END) { "Deflate failed during finish with error code $deflateResult" }

                val bytesWritten = BUFFER_SIZE - stream.avail_out.toInt()
                outputBuffer.write(compressionBuffer, 0, bytesWritten)

                outputLength += bytesWritten.toLong()
                outputPin.unpin()
            } while (deflateResult != Z_STREAM_END)

            return SdkBuffer().use {
                outputBuffer.readFully(it, outputLength)
                it.readByteArray()
            }
        }
    }

    override fun close() {
        if (isClosed) {
            return
        }

        deflateEnd(stream.ptr)
        nativeHeap.free(stream.ptr)
        isClosed = true
    }
}
