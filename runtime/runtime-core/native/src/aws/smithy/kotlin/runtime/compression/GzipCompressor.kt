/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.sdk.kotlin.crt.Closeable
import kotlinx.cinterop.*
import platform.zlib.*

/**
 * Streaming-style gzip compressor, implemented using zlib bindings
 */
internal class GzipCompressor : Closeable {
    companion object {
        internal const val BUFFER_SIZE = 16384
    }

    private val buffer = ByteArray(BUFFER_SIZE)
    private val stream = nativeHeap.alloc<z_stream>()
    private val outputBuffer = ArrayList<Byte>()
    internal var isClosed = false

    internal val availableForRead: Int
        get() = outputBuffer.size

    init {
        // Initialize deflate with gzip encoding
        val initResult = deflateInit2_(
            stream.ptr,
            Z_BEST_COMPRESSION,
            Z_DEFLATED,
            15 + 16, // Default windows bits (15) plus 16 for gzip encoding
            8, // Default memory level
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            sizeOf<z_stream>().toInt(),
        )

        check(initResult == Z_OK) { "Failed to initialize zlib deflate with error code $initResult: ${zError(initResult)!!.toKString()}" }
    }

    /**
     * Update the compressor with [input] bytes
     */
    fun update(input: ByteArray) = memScoped {
        val inputPin = input.pin()

        stream.next_in = inputPin.addressOf(0).reinterpret()
        stream.avail_in = input.size.toUInt()

        while (stream.avail_in > 0u) {
            val outputPin = buffer.pin()
            stream.next_out = outputPin.addressOf(0).reinterpret()
            stream.avail_out = BUFFER_SIZE.toUInt()

            val deflateResult = deflate(stream.ptr, Z_NO_FLUSH)
            check(deflateResult == Z_OK) { "Deflate failed with error code $deflateResult" }

            val bytesWritten = BUFFER_SIZE - stream.avail_out.toInt()
            outputBuffer.addAll(buffer.take(bytesWritten))

            outputPin.unpin()
        }

        inputPin.unpin()
    }

    /**
     * Consume [count] gzip-compressed bytes.
     */
    fun consume(count: Int): ByteArray {
        require(count in 0..availableForRead) {
            "Count must be between 0 and $availableForRead, got $count"
        }

        val result = outputBuffer.take(count).toByteArray()
        repeat(count) { outputBuffer.removeAt(0) }
        return result
    }

    /**
     * Flush the compressor and return the terminal sequence of bytes that represent the end of the gzip compression.
     */
    fun flush(): ByteArray {
        if (isClosed) {
            return byteArrayOf()
        }

        memScoped {
            var finished = false

            while (!finished) {
                val outputPin = buffer.pin()
                stream.next_out = outputPin.addressOf(0).reinterpret()
                stream.avail_out = BUFFER_SIZE.toUInt()

                val deflateResult = deflate(stream.ptr, Z_FINISH)
                if (deflateResult != Z_STREAM_END && deflateResult != Z_OK) {
                    throw RuntimeException("Deflate failed during finish with error code $deflateResult")
                }

                val bytesWritten = BUFFER_SIZE - stream.avail_out.toInt()
                outputBuffer.addAll(buffer.take(bytesWritten))

                finished = deflateResult == Z_STREAM_END
                outputPin.unpin()
            }

            return outputBuffer.toByteArray()
        }
    }

    override fun close() {
        if (isClosed) { return }

        deflateEnd(stream.ptr)
        nativeHeap.free(stream.ptr)
        isClosed = true
    }
}
