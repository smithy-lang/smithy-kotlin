/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

internal fun SdkBuffer.hasArray() = memory.buffer.hasArray() && !memory.buffer.isReadOnly

actual fun SdkBuffer.bytes(): ByteArray {
    return when (hasArray()) {
        true -> memory.buffer.array().sliceArray(readPosition until readRemaining)
        false -> ByteArray(readRemaining).apply { readFully(this) }
    }
}
