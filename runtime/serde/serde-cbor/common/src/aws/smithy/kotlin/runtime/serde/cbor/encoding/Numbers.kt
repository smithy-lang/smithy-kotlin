/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.cbor.encodeMajorMinor
import aws.smithy.kotlin.runtime.serde.cbor.writeArgument

/**
 * Write a CBOR unsigned integer (major type 0) in the range [0, 2^64-1].
 */
internal fun SdkBufferedSink.writeUInt(value: ULong) = writeArgument(Major.U_INT, value)

internal fun decodeUInt(buffer: SdkBufferedSource): ULong = decodeArgument(buffer)

/**
 * Write a CBOR negative integer (major type 1) in the range [-2^64, -1].
 *
 * [value] is the magnitude of the negative number; it is encoded / decoded according to the CBOR specification
 * (-1 minus the encoded argument).
 */
internal fun SdkBufferedSink.writeNegInt(value: ULong) = writeArgument(Major.NEG_INT, value - 1u)

internal fun decodeNegInt(buffer: SdkBufferedSource): ULong = decodeArgument(buffer) + 1u

/**
 * Decode a CBOR 16-bit float (major type 7, minor type 25) as a [Float].
 * Note: This CBOR type can only be *decoded*, it will never be encoded.
 */
internal fun decodeFloat16(buffer: SdkBufferedSource): Float {
    buffer.readByte() // discard head byte
    val float16Bits: Int = buffer.readShort().toInt() and 0xffff

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

    return Float.fromBits(float32)
}

/**
 * Write a CBOR 32-bit float (major type 7, minor type 26).
 */
internal fun SdkBufferedSink.writeFloat32(value: Float) {
    writeByte(encodeMajorMinor(Major.TYPE_7, Minor.FLOAT32))
    writeInt(value.toRawBits())
}

internal fun decodeFloat32(buffer: SdkBufferedSource): Float {
    buffer.readByte() // discard head byte
    return Float.fromBits(buffer.readInt())
}

/**
 * Write a CBOR 64-bit float (major type 7, minor type 27).
 */
internal fun SdkBufferedSink.writeFloat64(value: Double) {
    writeByte(encodeMajorMinor(Major.TYPE_7, Minor.FLOAT64))
    writeLong(value.toRawBits())
}

internal fun decodeFloat64(buffer: SdkBufferedSource): Double {
    buffer.readByte() // discard head byte
    return Double.fromBits(buffer.readLong())
}
