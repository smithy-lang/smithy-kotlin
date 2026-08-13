/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.cbor.encoding.*
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Major
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Minor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.peekMajor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.peekMinorByte
import kotlin.math.absoluteValue

/**
 * Encode and write a CBOR [Value] to this [SdkBuffer]
 */
internal fun SdkBuffer.write(value: Value) = value.encode(this)

// Peek at the head byte to determine if the next encoded value represents a break in an indefinite-length list/map
internal val SdkBufferedSource.nextValueIsIndefiniteBreak: kotlin.Boolean
    get() = peekMajor(this) == Major.TYPE_7 && peekMinorByte(this) == Minor.INDEFINITE.value

// Peek at the head byte to determine if the next encoded value represents null
internal val SdkBufferedSource.nextValueIsNull: kotlin.Boolean
    get() = peekMajor(this) == Major.TYPE_7 && (peekMinorByte(this) == Minor.NULL.value || peekMinorByte(this) == Minor.UNDEFINED.value)

// Encodes a [Major] and [Minor] value in a single byte
internal fun encodeMajorMinor(major: Major, minor: Minor): Byte = (major.value.toUInt() shl 5 or minor.value.toUInt()).toByte()

// Encode a [Major] value along with its additional information / argument, writing directly to [into].
// CBOR arguments are big-endian; using fixed-width writes avoids allocating an intermediate ByteArray
// (and boxing each byte) on every integer / length / tag id that is serialized. Produces byte-for-byte
// identical output to the previous ByteArray-building encoder.
internal fun SdkBufferedSink.writeArgument(major: Major, argument: ULong) {
    val majorBits = major.ordinal shl 5
    when {
        argument < 24u -> writeByte((majorBits.toULong() or argument).toByte())
        argument < 0x100u -> {
            writeByte((majorBits or Minor.ARG_1.value.toInt()).toByte())
            writeByte(argument.toByte())
        }
        argument < 0x10000u -> {
            writeByte((majorBits or Minor.ARG_2.value.toInt()).toByte())
            writeShort(argument.toShort())
        }
        argument < 0x100000000u -> {
            writeByte((majorBits or Minor.ARG_4.value.toInt()).toByte())
            writeInt(argument.toInt())
        }
        else -> {
            writeByte((majorBits or Minor.ARG_8.value.toInt()).toByte())
            writeLong(argument.toLong())
        }
    }
}

// Encode a CBOR 32-bit float (major type 7, minor 26) directly: head byte + big-endian raw bits.
// Shared by [CborSerializer.serializeFloat] and [Float32.encode] so the byte layout has a single source of truth.
internal fun SdkBufferedSink.writeFloat32(value: Float) {
    writeByte(encodeMajorMinor(Major.TYPE_7, Minor.FLOAT32))
    writeInt(value.toRawBits())
}

// Encode a CBOR 64-bit float (major type 7, minor 27) directly: head byte + big-endian raw bits.
// Shared by [CborSerializer.serializeDouble]/[serializeInstant], [Float64.encode], and [Timestamp.encode].
internal fun SdkBufferedSink.writeFloat64(value: Double) {
    writeByte(encodeMajorMinor(Major.TYPE_7, Minor.FLOAT64))
    writeLong(value.toRawBits())
}

// Encode a CBOR text string (major type 3): byte-length argument + UTF-8 bytes (length is a byte count, RFC 8949 §3.1).
// Shared by [CborSerializer.serializeString]/[serializeChar] and [TextString.encode].
internal fun SdkBufferedSink.writeText(value: String) {
    val bytes = value.encodeToByteArray()
    writeArgument(Major.STRING, bytes.size.toULong())
    write(bytes)
}

// Encode a CBOR byte string (major type 2): byte-length argument + raw bytes.
// Shared by [CborSerializer.serializeByteArray] and [ByteString.encode].
internal fun SdkBufferedSink.writeBytes(value: ByteArray) {
    writeArgument(Major.BYTE_STRING, value.size.toULong())
    write(value)
}

private val BIG_INTEGER_ONE = BigInteger("1")
internal val BIG_INTEGER_ZERO = BigInteger("0")
private val LONG_MIN = BigInteger(Long.MIN_VALUE.toString())
private val LONG_MAX = BigInteger(Long.MAX_VALUE.toString())

// Encode a CBOR unsigned bignum (tag 2 + byte string of the big-endian magnitude).
// Shared by [CborSerializer.serializeBigInteger] and [BigNum.encode].
internal fun SdkBufferedSink.writeBigNum(value: BigInteger) {
    writeArgument(Major.TAG, TagId.BIG_NUM.value)
    writeBytes(value.toByteArray())
}

// Encode a CBOR negative bignum (tag 3 + byte string of (-1 - value), i.e. (value - 1)).
// Shared by [CborSerializer.serializeBigInteger] and [NegBigNum.encode].
internal fun SdkBufferedSink.writeNegBigNum(value: BigInteger) {
    writeArgument(Major.TAG, TagId.NEG_BIG_NUM.value)
    writeBytes((value - BIG_INTEGER_ONE).toByteArray())
}

// Encode a CBOR decimal fraction (tag 4 + a definite length-2 list of [exponent, mantissa]) directly,
// avoiding the Tag/List/listOf and per-element integer/bignum wrapper allocations.
// Shared by [CborSerializer.serializeBigDecimal] and [DecimalFraction.encode].
internal fun SdkBufferedSink.writeDecimalFraction(value: BigDecimal) {
    val cborExponent = -value.exponent // CBOR has inverted exponent semantics

    writeArgument(Major.TAG, TagId.DECIMAL_FRACTION.value)
    writeArgument(Major.LIST, 2uL)

    // Exponent: always fits in an Int, encoded as a CBOR unsigned/negative integer.
    if (cborExponent < 0) {
        writeArgument(Major.NEG_INT, cborExponent.absoluteValue.toULong() - 1u)
    } else {
        writeArgument(Major.U_INT, cborExponent.toULong())
    }

    // Mantissa: a plain CBOR integer when it fits in Long range, otherwise a (negative) bignum.
    val mantissa = value.mantissa
    if (mantissa > LONG_MIN && mantissa < LONG_MAX) {
        val m = mantissa.toLong()
        if (m < 0) {
            writeArgument(Major.NEG_INT, m.absoluteValue.toULong() - 1u)
        } else {
            writeArgument(Major.U_INT, m.toULong())
        }
    } else if (mantissa < BIG_INTEGER_ZERO) {
        writeNegBigNum(mantissa)
    } else {
        writeBigNum(mantissa)
    }
}

// Convert a ByteArray to a ULong by left-shifting each byte appropriately
internal fun ByteArray.toULong() = foldIndexed(0uL) { i, acc, byte ->
    acc or (byte.toUByte().toULong() shl ((size - 1 - i) * 8))
}
