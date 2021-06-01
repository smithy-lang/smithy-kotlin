/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import io.ktor.utils.io.bits.*
import java.nio.ByteBuffer

internal fun SdkBuffer.hasArray() = memory.buffer.hasArray() && !memory.buffer.isReadOnly

actual fun SdkBuffer.bytes(): ByteArray = when (hasArray()) {
    true -> memory.buffer.array().sliceArray(readPosition until readRemaining)
    false -> ByteArray(readRemaining).apply { readFully(this) }
}

internal actual fun Memory.Companion.ofByteArray(src: ByteArray, offset: Int, length: Int): Memory =
    Memory.of(src, offset, length)

/**
 * Create a new SdkBuffer using the given [ByteBuffer] as the contents
 */
fun SdkBuffer.Companion.of(byteBuffer: ByteBuffer): SdkBuffer = SdkBuffer(Memory.of(byteBuffer))

/**
 * Read the buffer's content to the [dst] buffer moving it's position.
 */
fun SdkBuffer.readFully(dst: ByteBuffer) {
    val length = dst.remaining()
    read { memory, readStart, _ ->
        memory.copyTo(dst, readStart)
        length
    }
}

/**
 * Read as much from this buffer as possible to [dst] buffer moving it's position
 */
fun SdkBuffer.readAvailable(dst: ByteBuffer) {
    val wc = minOf(readRemaining, dst.remaining())
    if (wc == 0) return
    val dstCopy = dst.duplicate().apply {
        limit(position() + wc)
    }
    readFully(dstCopy)
    dst.position(dst.position() + wc)
}
