/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.cbor.encodeMajorMinor

/**
 * Write a CBOR boolean (major type 7). The minor type is 5 for false and 6 for true.
 */
internal fun SdkBufferedSink.writeBoolean(value: Boolean) = writeByte(
    encodeMajorMinor(Major.TYPE_7, if (value) Minor.TRUE else Minor.FALSE),
)

internal fun decodeBooleanValue(buffer: SdkBufferedSource): Boolean = when (val minor = peekMinorByte(buffer)) {
    Minor.FALSE.value -> false
    Minor.TRUE.value -> true
    else -> throw DeserializationException("Unknown minor argument $minor for Boolean")
}.also {
    buffer.readByte()
}

/**
 * Write a CBOR null value (major type 7, minor type 7).
 */
internal fun SdkBufferedSink.writeNull() = writeByte(encodeMajorMinor(Major.TYPE_7, Minor.NULL))

/**
 * Consume the head byte of a CBOR null (or undefined) value.
 */
internal fun decodeNull(buffer: SdkBufferedSource) {
    buffer.readByte()
}

/**
 * Write the "break" stop-code which terminates a list/map of indefinite length (major type 7, minor type 31).
 */
internal fun SdkBufferedSink.writeIndefiniteBreak() = writeByte(encodeMajorMinor(Major.TYPE_7, Minor.INDEFINITE))

/**
 * Consume the "break" stop-code which terminates a list/map of indefinite length.
 */
internal fun decodeIndefiniteBreak(buffer: SdkBufferedSource) {
    buffer.readByte()
}
