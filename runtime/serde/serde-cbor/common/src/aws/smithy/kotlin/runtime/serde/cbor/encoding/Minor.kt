/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.io.peekByte
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

/**
 * Peek the head byte (major + minor) of the next CBOR value without consuming it.
 *
 * `SdkBufferedSource.peek()` allocates on every call, so callers that need both the major and the minor
 * type should peek **once** via this and derive both with [majorOf]/[minorOf] rather than peeking the
 * major and minor separately. On paths where the head byte is always consumed, prefer reading it once
 * with `readByte()` (non-allocating) over peeking at all.
 */
// Read the next head byte without consuming it. CBOR always decodes from a fully-buffered SdkBuffer, so
// we peek the byte directly from the in-memory buffer (allocation-free) instead of buffer.peek(), which
// allocates a fresh buffered source on every call. This is the hot lookahead used by nextValueIsNull /
// nextValueIsIndefiniteBreak (called for every element/entry/field of indefinite-length containers).
internal fun peekHead(buffer: SdkBufferedSource): UByte = buffer.buffer.peekByte().toUByte()

internal fun minorOf(head: UByte): UByte = head and MINOR_BYTE_MASK

internal fun peekMinorByte(buffer: SdkBufferedSource): UByte = minorOf(peekHead(buffer))

internal fun decodeArgument(buffer: SdkBufferedSource): ULong = decodeArgument(buffer, buffer.readByte().toUByte())

/**
 * Decode a CBOR argument from an already-read [head] byte.
 *
 * Callers that must inspect the head byte first (e.g. to derive the [Major] type, or to distinguish an
 * indefinite-length marker) can read it a single time and pass it here, avoiding the extra `peek()`
 * allocation that reading the major/minor separately would incur on every integer / length / tag id.
 */
internal fun decodeArgument(buffer: SdkBufferedSource, head: UByte): ULong {
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
