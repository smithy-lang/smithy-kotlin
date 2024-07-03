/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.io.readFully
import aws.smithy.kotlin.runtime.io.use
import aws.smithy.kotlin.runtime.serde.DeserializationException

/**
 * Represents CBOR minor types (aka "additional information")
 */
internal enum class Minor(val value: UByte) {
    ARG_1(24u),
    ARG_2(25u),
    ARG_4(26u),
    ARG_8(27u),
    INDEFINITE(31u),

    // The following minor values are only to be used with major type 7
    FALSE(20u),
    TRUE(21u),
    NULL(22u),
    UNDEFINED(23u), // note: undefined should be deserialized to `null`
    FLOAT16(25u),
    FLOAT32(26u),
    FLOAT64(27u),
}

internal val MINOR_BYTE_MASK: UByte = 0b11111u

internal fun peekMinorByte(buffer: SdkBufferedSource): UByte {
    val byte = buffer.peek().readByte().toUByte()
    return byte and MINOR_BYTE_MASK
}

internal fun decodeArgument(buffer: SdkBufferedSource): ULong {
    val minor = buffer.readByte().toUByte() and MINOR_BYTE_MASK

    if (minor < Minor.ARG_1.value) {
        return minor.toULong()
    }

    val numBytes = when (minor) {
        Minor.ARG_1.value -> 1L
        Minor.ARG_2.value -> 2L
        Minor.ARG_4.value -> 4L
        Minor.ARG_8.value -> 8L
        else -> throw DeserializationException("Unsupported minor value $minor, expected one of ${Minor.ARG_1.value}, ${Minor.ARG_2.value}, ${Minor.ARG_4.value}, ${Minor.ARG_8.value}")
    }

    val bytes = SdkBuffer().use {
        buffer.readFully(it, numBytes)
        it.readByteArray()
    }
    return bytes.toULong()
}
