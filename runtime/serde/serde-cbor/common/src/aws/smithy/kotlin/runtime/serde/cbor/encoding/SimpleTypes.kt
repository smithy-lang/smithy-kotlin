/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.cbor.encodeArgument
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
        internal fun decode(buffer: SdkBufferedSource): Boolean = when (val minor = peekMinorByte(buffer)) {
            Minor.FALSE.value -> Boolean(false)
            Minor.TRUE.value -> Boolean(true)
            else -> throw DeserializationException("Unknown minor argument $minor for Boolean")
        }.also {
            buffer.readByte()
        }
    }
}

/**
 * Represents a CBOR null value (major type 7, minor type 7).
 */
internal object Null : Value {
    override fun encode(into: SdkBufferedSink) = into.writeByte(encodeMajorMinor(Major.TYPE_7, Minor.NULL))

    internal fun decode(buffer: SdkBufferedSource): Null {
        buffer.readByte() // consume the byte
        return Null
    }
}

/**
 * Represents the "break" stop-code for lists/maps with an indefinite length (major type 7, minor type 31).
 */
internal object IndefiniteBreak : Value {
    override fun encode(into: SdkBufferedSink) = into.writeByte(encodeMajorMinor(Major.TYPE_7, Minor.INDEFINITE))

    internal fun decode(buffer: SdkBufferedSource): IndefiniteBreak {
        buffer.readByte()
        return IndefiniteBreak
    }
}
