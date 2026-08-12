/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.cbor.CborReader
import aws.smithy.kotlin.runtime.serde.cbor.encodeMajorMinor

/**
 * Represents a CBOR boolean (major type 7). The minor type is 5 for false and 6 for true.
 * @param value the [kotlin.Boolean] this CBOR boolean represents.
 */
internal class Boolean(val value: kotlin.Boolean) : Value {
    override fun encode(into: SdkBufferedSink) = into.writeByte(
        when (value) {
            false -> encodeMajorMinor(Major.TYPE_7, Minor.FALSE)
            true -> encodeMajorMinor(Major.TYPE_7, Minor.TRUE)
        },
    )

    internal companion object {
        internal fun decode(buffer: CborReader): Boolean = Boolean(decodeValue(buffer))

        // Decode the value directly without allocating a [Boolean] wrapper (hot deserialize path). The head
        // byte is always consumed, so read it once.
        internal fun decodeValue(buffer: CborReader): kotlin.Boolean = when (val minor = minorOf(buffer.readByte().toUByte())) {
            Minor.FALSE.value -> false
            Minor.TRUE.value -> true
            else -> throw DeserializationException("Unknown minor argument $minor for Boolean")
        }
    }
}

/**
 * Represents a CBOR null value (major type 7, minor type 7).
 */
internal object Null : Value {
    override fun encode(into: SdkBufferedSink) = into.writeByte(encodeMajorMinor(Major.TYPE_7, Minor.NULL))

    internal fun decode(buffer: CborReader): Null {
        buffer.readByte() // consume the byte
        return Null
    }
}

/**
 * Represents the "break" stop-code for lists/maps with an indefinite length (major type 7, minor type 31).
 */
internal object IndefiniteBreak : Value {
    override fun encode(into: SdkBufferedSink) = into.writeByte(encodeMajorMinor(Major.TYPE_7, Minor.INDEFINITE))

    internal fun decode(buffer: CborReader): IndefiniteBreak {
        buffer.readByte()
        return IndefiniteBreak
    }
}
