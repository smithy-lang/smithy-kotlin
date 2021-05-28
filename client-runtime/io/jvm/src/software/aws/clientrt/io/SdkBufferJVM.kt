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
