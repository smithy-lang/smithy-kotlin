/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.cbor.CborReader

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

// Read the next head byte (major + minor) without consuming it. [CborReader] peeks directly from the
// in-memory payload, so this allocates nothing — unlike SdkBuffer.peek(). Hot lookahead used by
// nextValueIsNull / nextValueIsIndefiniteBreak (every element/entry/field of indefinite containers).
internal fun peekHead(buffer: CborReader): UByte = buffer.peekByte().toUByte()

internal fun minorOf(head: UByte): UByte = head and MINOR_BYTE_MASK

internal fun peekMinorByte(buffer: CborReader): UByte = minorOf(peekHead(buffer))

internal fun decodeArgument(buffer: CborReader): ULong = decodeArgument(buffer, buffer.readByte().toUByte())

/**
 * Decode a CBOR argument from an already-read [head] byte.
 *
 * Callers that must inspect the head byte first (e.g. to derive the [Major] type, or to distinguish an
 * indefinite-length marker) can read it a single time and pass it here, avoiding a re-read.
 */
internal fun decodeArgument(buffer: CborReader, head: UByte): ULong {
    val minor = head and MINOR_BYTE_MASK

    if (minor < Minor.ARG_1.value) {
        return minor.toULong()
    }

    // CBOR arguments are big-endian; read them with fixed-width reads to avoid allocating a temporary
    // buffer + byte array on every integer / length / tag id.
    return when (minor) {
        Minor.ARG_1.value -> buffer.readByte().toUByte().toULong()
        Minor.ARG_2.value -> buffer.readShort().toUShort().toULong()
        Minor.ARG_4.value -> buffer.readInt().toUInt().toULong()
        Minor.ARG_8.value -> buffer.readLong().toULong()
        else -> throw DeserializationException("Unsupported minor value $minor, expected one of ${Minor.ARG_1.value}, ${Minor.ARG_2.value}, ${Minor.ARG_4.value}, ${Minor.ARG_8.value}")
    }
}
