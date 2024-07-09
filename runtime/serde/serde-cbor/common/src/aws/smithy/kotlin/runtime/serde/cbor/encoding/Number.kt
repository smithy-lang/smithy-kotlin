/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.SerializationException
import aws.smithy.kotlin.runtime.serde.cbor.encodeArgument
import aws.smithy.kotlin.runtime.serde.cbor.encodeMajorMinor
import aws.smithy.kotlin.runtime.serde.cbor.toULong

/**
 * Represents a CBOR unsigned integer (major type 0) in the range [0, 2^64-1].
 * @param value The [ULong] value which this unsigned integer represents.
 */
internal class UInt(val value: ULong) : Value {
    override fun encode(into: SdkBufferedSink) = into.write(encodeArgument(Major.U_INT, value))

    internal companion object {
        fun decode(buffer: SdkBufferedSource) = UInt(decodeArgument(buffer))
    }
}

/**
 * Represents a CBOR negative integer (major type 1) in the range [-2^64, -1].
 * @param value The [ULong] value which this unsigned integer represents.
 *
 * Values will be properly encoded / decoded according to the CBOR specification (-1 minus $value)
 */
internal class NegInt(val value: ULong) : Value {
    override fun encode(into: SdkBufferedSink) = into.write(encodeArgument(Major.NEG_INT, value - 1u))

    internal companion object {
        fun decode(buffer: SdkBufferedSource): NegInt {
            val argument: ULong = decodeArgument(buffer)
            return NegInt(argument + 1u)
        }
    }
}

/**
 * Represents a CBOR 16-bit float (major type 7, minor type 25).
 * Note: This CBOR type can only be *decoded*, it will never be encoded.
 * @param value the [Float] that this CBOR 16-bit float represents.
 */
internal class Float16(val value: Float) : Value {
    override fun encode(into: SdkBufferedSink) = throw SerializationException("Encoding of CBOR 16-bit floats is not supported")

    internal companion object {
        fun decode(buffer: SdkBufferedSource): Float16 {
            buffer.readByte() // discard head byte
            val bytes = buffer.readByteArray(2)

            val float16Bits: Int = ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)

            val sign = (float16Bits and (0x1 shl 15)) shl 16 // top bit
            val exponent = (float16Bits and (0x1f shl 10)) shr 10 // next 5 bits
            val fraction = (float16Bits and 0x3ff) shl 13 // remaining 10 bits

            val float32 = when (exponent) {
                0x1F -> sign or 0x7F800000 or fraction // Infinity / NaN
                0 -> {
                    if (fraction == 0) {
                        sign // Zero
                    } else {
                        // Subnormal numbers
                        var subnormalFraction = fraction
                        var e = -14 + 127
                        while (subnormalFraction and 0x800000 == 0) {
                            subnormalFraction = subnormalFraction shl 1
                            e -= 1
                        }
                        sign or (e shl 23) or (subnormalFraction and 0x7FFFFF)
                    }
                }
                else -> sign or ((exponent + (127 - 15)) shl 23) or fraction // Normalized numbers
            }

            return Float16(Float.fromBits(float32))
        }
    }
}

/**
 * Represents a CBOR 32-bit float (major type 7, minor type 26).
 * @param value the [Float] that this CBOR 32-bit float represents.
 */
internal class Float32(val value: Float) : Value {
    override fun encode(into: SdkBufferedSink) {
        val bits: Int = value.toRawBits()

        val bytes = (24 downTo 0 step 8).map { shiftAmount ->
            (bits shr shiftAmount and 0xff).toByte()
        }.toByteArray()

        into.writeByte(encodeMajorMinor(Major.TYPE_7, Minor.FLOAT32))
        into.write(bytes)
    }

    internal companion object {
        fun decode(buffer: SdkBufferedSource): Float32 {
            buffer.readByte() // discard head byte
            val bytes = buffer.readByteArray(4)
            return Float32(Float.fromBits(bytes.toULong().toInt()))
        }
    }
}

/**
 * Represents a CBOR 64-bit float (major type 7, minor type 27).
 * @param value the [Double] that this CBOR 64-bit float represents
 */
internal class Float64(val value: Double) : Value {
    override fun encode(into: SdkBufferedSink) {
        val bits: Long = value.toRawBits()
        val bytes = (56 downTo 0 step 8).map { shiftAmount ->
            (bits shr shiftAmount and 0xff).toByte()
        }.toByteArray()

        into.writeByte(encodeMajorMinor(Major.TYPE_7, Minor.FLOAT64))
        into.write(bytes)
    }

    internal companion object {
        fun decode(buffer: SdkBufferedSource): Float64 {
            buffer.readByte() // discard head byte
            val bytes = buffer.readByteArray(8)
            return Float64(Double.fromBits(bytes.toULong().toLong()))
        }
    }
}