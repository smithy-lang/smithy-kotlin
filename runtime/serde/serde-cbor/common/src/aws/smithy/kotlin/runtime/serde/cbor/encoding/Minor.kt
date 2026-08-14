/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.io.internal.headByte
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

internal fun peekHead(buffer: SdkBufferedSource): UByte = when (buffer) {
    is SdkBuffer -> buffer.headByte().toUByte()
    else -> buffer.peek().readByte().toUByte()
}

internal fun minorOf(head: UByte): UByte = head and MINOR_BYTE_MASK

internal fun peekMinorByte(buffer: SdkBufferedSource): UByte = minorOf(peekHead(buffer))

internal fun decodeArgument(buffer: SdkBufferedSource): ULong {
    val minor = buffer.readByte().toUByte() and MINOR_BYTE_MASK

    if (minor < Minor.ARG_1.value) {
        return minor.toULong()
    }

    return when (minor) {
        Minor.ARG_1.value -> buffer.readByte().toUByte().toULong()
        Minor.ARG_2.value -> buffer.readShort().toUShort().toULong()
        Minor.ARG_4.value -> buffer.readInt().toUInt().toULong()
        Minor.ARG_8.value -> buffer.readLong().toULong()
        else -> throw DeserializationException("Unsupported minor value $minor, expected one of ${Minor.ARG_1.value}, ${Minor.ARG_2.value}, ${Minor.ARG_4.value}, ${Minor.ARG_8.value}")
    }
}
