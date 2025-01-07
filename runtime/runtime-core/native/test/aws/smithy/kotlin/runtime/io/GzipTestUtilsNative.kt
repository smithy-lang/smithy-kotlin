/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

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
 * Decompresses a byte array compressed using the gzip format
 */
internal actual fun decompressGzipBytes(bytes: ByteArray): ByteArray {
    if (bytes.isEmpty()) {
        return bytes
    }

    val decompressedBuffer = UByteArray(bytes.size * 2) // Initial guess for decompressed size (may expand)

    memScoped {
        val zStream = alloc<z_stream>().apply {
            zalloc = null
            zfree = null
            opaque = null
        }

        // Initialize the inflate context for gzip decoding
        val result = inflateInit2_(
            strm = zStream.ptr,
            windowBits = 31,
            version = ZLIB_VERSION,
            stream_size = sizeOf<z_stream>().toInt(),
        )
        if (result != Z_OK) {
            throw IllegalStateException("inflateInit2_ failed with error code $result")
        }

        try {
            zStream.next_in = bytes.refTo(0).getPointer(memScope).reinterpret()
            zStream.avail_in = bytes.size.toUInt()

            val output = mutableListOf<UByte>()
            while (zStream.avail_in > 0u) {
                decompressedBuffer.usePinned { pinnedBuffer ->
                    zStream.next_out = pinnedBuffer.addressOf(0)
                    zStream.avail_out = decompressedBuffer.size.toUInt()

                    val inflateResult = inflate(zStream.ptr, Z_NO_FLUSH)

                    when (inflateResult) {
                        Z_OK, Z_STREAM_END -> {
                            val chunkSize = decompressedBuffer.size.toUInt() - zStream.avail_out
                            output.addAll(decompressedBuffer.copyOf(chunkSize.toInt()))
                        }
                        else -> throw IllegalStateException("Decompression failed with error code $inflateResult")
                    }

                    if (inflateResult == Z_STREAM_END) {
                        return@usePinned true
                    }
                }
            }
            return output.toUByteArray().toByteArray()
        } finally {
            inflateEnd(zStream.ptr)
        }
    }
}
