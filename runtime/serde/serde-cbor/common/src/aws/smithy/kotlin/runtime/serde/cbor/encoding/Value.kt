/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.DeserializationRecursionException
import aws.smithy.kotlin.runtime.serde.cbor.nextValueIsIndefiniteBreak

/**
 * Represents an encodable / decodable CBOR value.
 */
internal interface Value {
    /**
     * Encode this [Value] by writing its bytes [into] an [SdkBuffer]
     * @param into the [SdkBufferedSink] to encode into
     */
    fun encode(into: SdkBufferedSink)

    companion object {
        /**
         * Decode a [Value] from the given [buffer]
         * @param buffer the [SdkBufferedSource] to read the next [Value] from
         * @param depth the current recursion depth
         */
        fun decode(buffer: SdkBufferedSource, depth: Int = 0): Value {
            DeserializationRecursionException.assertDepth(depth)

            val head = peekByte(buffer)
            val major = majorOf(head)
            val minor = minorOf(head)

            return when (major) {
                Major.U_INT -> UInt.decode(buffer)
                Major.NEG_INT -> NegInt.decode(buffer)
                Major.BYTE_STRING -> ByteString.decode(buffer, depth)
                Major.STRING -> TextString.decode(buffer, depth)

                Major.LIST -> if (minor == Minor.INDEFINITE.value) {
                    IndefiniteList.decode(buffer, depth)
                } else {
                    List.decode(buffer, depth)
                }

                Major.MAP -> if (minor == Minor.INDEFINITE.value) {
                    IndefiniteMap.decode(buffer, depth)
                } else {
                    Map.decode(buffer, depth)
                }

                Major.TAG -> Tag.decode(buffer, depth)

                Major.TYPE_7 -> when (minor) {
                    Minor.TRUE.value -> Boolean.decode(buffer)
                    Minor.FALSE.value -> Boolean.decode(buffer)
                    Minor.NULL.value -> Null.decode(buffer)
                    Minor.UNDEFINED.value -> Null.decode(buffer)
                    Minor.FLOAT16.value -> Float16.decode(buffer)
                    Minor.FLOAT32.value -> Float32.decode(buffer)
                    Minor.FLOAT64.value -> Float64.decode(buffer)
                    Minor.INDEFINITE.value -> IndefiniteBreak.decode(buffer)
                    else -> throw DeserializationException("Unexpected type 7 minor value $minor")
                }
            }
        }
    }
}

/**
 * Advance [buffer] past exactly one encoded CBOR value without materializing it. Used to skip unknown or
 * ignored fields during struct/union deserialization; unlike [Value.decode] it allocates nothing (no
 * wrapper objects, collections, or decoded strings) — it only reads head bytes and advances the cursor.
 * @param buffer the [SdkBufferedSource] to advance
 * @param depth the current recursion depth
 */
internal fun skipValue(buffer: SdkBufferedSource, depth: Int = 0) {
    DeserializationRecursionException.assertDepth(depth)

    val head = peekByte(buffer)
    val minor = minorOf(head)

    when (majorOf(head)) {
        Major.U_INT, Major.NEG_INT -> decodeArgument(buffer)

        Major.BYTE_STRING, Major.STRING ->
            if (minor == Minor.INDEFINITE.value) {
                buffer.readByte() // discard indefinite head byte
                while (!buffer.nextValueIsIndefiniteBreak) skipValue(buffer, depth + 1)
                buffer.readByte() // discard break byte
            } else {
                buffer.skip(decodeArgument(buffer).toLong())
            }

        Major.LIST ->
            if (minor == Minor.INDEFINITE.value) {
                buffer.readByte() // discard indefinite head byte
                while (!buffer.nextValueIsIndefiniteBreak) skipValue(buffer, depth + 1)
                buffer.readByte() // discard break byte
            } else {
                var remaining = decodeArgument(buffer)
                while (remaining > 0uL) {
                    skipValue(buffer, depth + 1)
                    remaining--
                }
            }

        Major.MAP ->
            if (minor == Minor.INDEFINITE.value) {
                buffer.readByte() // discard indefinite head byte
                while (!buffer.nextValueIsIndefiniteBreak) {
                    skipValue(buffer, depth + 1) // key
                    skipValue(buffer, depth + 1) // value
                }
                buffer.readByte() // discard break byte
            } else {
                var remaining = decodeArgument(buffer)
                while (remaining > 0uL) {
                    skipValue(buffer, depth + 1) // key
                    skipValue(buffer, depth + 1) // value
                    remaining--
                }
            }

        // Tags carry per-id validation (supported ids, decimal-fraction shape) and their own depth
        // accounting, so delegate to the full decoder to keep skip behavior byte- and error-identical.
        // Tagged values are rare in skipped/unknown fields, so the wrapper cost here is negligible.
        Major.TAG -> Value.decode(buffer, depth)

        Major.TYPE_7 -> when (minor) {
            Minor.FLOAT16.value -> {
                buffer.readByte()
                buffer.skip(2)
            }
            Minor.FLOAT32.value -> {
                buffer.readByte()
                buffer.skip(4)
            }
            Minor.FLOAT64.value -> {
                buffer.readByte()
                buffer.skip(8)
            }
            // true / false / null / undefined / break: a single head byte with no payload.
            Minor.TRUE.value, Minor.FALSE.value, Minor.NULL.value, Minor.UNDEFINED.value, Minor.INDEFINITE.value ->
                buffer.readByte()
            // Match Value.decode: reject simple values / reserved minors rather than mis-advancing the cursor.
            else -> throw DeserializationException("Unexpected type 7 minor value $minor")
        }
    }
}
