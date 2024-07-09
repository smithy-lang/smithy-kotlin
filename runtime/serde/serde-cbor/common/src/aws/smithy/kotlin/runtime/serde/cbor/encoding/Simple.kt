/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.cbor.encodeArgument
import aws.smithy.kotlin.runtime.serde.cbor.encodeMajorMinor
import aws.smithy.kotlin.runtime.serde.cbor.toULong

/**
 * Represents a CBOR string (major type 3) encoded as a UTF-8 byte array.
 * @param value The [String] which this CBOR string represents.
 */
internal class String(val value: kotlin.String) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.write(encodeArgument(Major.STRING, value.length.toULong()))
        into.write(value.encodeToByteArray())
    }

    internal companion object {
        fun decode(buffer: SdkBufferedSource): String =
            if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
                val list = IndefiniteList.decode(buffer).value

                val sb = StringBuilder()
                list.forEach {
                    sb.append((it as String).value)
                }

                String(sb.toString())
            } else {
                val length = decodeArgument(buffer).toInt()

                val bytes = SdkBuffer().use {
                    buffer.readFully(it, length.toLong())
                    it.readByteArray()
                }

                String(bytes.decodeToString())
            }
    }
}

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
internal class Null : Value {
    override fun encode(into: SdkBufferedSink) = into.writeByte(encodeMajorMinor(Major.TYPE_7, Minor.NULL))

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): Null {
            buffer.readByte() // consume the byte
            return Null()
        }
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
