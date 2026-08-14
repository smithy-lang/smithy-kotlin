/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.cbor.writeArgument
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlin.math.absoluteValue

private val NEGATIVE_ONE = BigInteger("-1")
private val MIN_LONG_AS_BIG_INTEGER = BigInteger(Long.MIN_VALUE.toString())
private val MAX_LONG_AS_BIG_INTEGER = BigInteger(Long.MAX_VALUE.toString())

internal enum class TagId(val value: ULong) {
    TIMESTAMP(1uL),
    BIG_NUM(2uL),
    NEG_BIG_NUM(3uL),
    DECIMAL_FRACTION(4uL),
}

/**
 * Represents a tagged CBOR [Value] (major type 6). The minor type describes the contents of the tagged value:
 * - 1 -> Timestamp (encoded as epoch seconds)
 * - 2 -> Unsigned bignum
 * - 3 -> Negative bignum
 * - 4 -> Decimal fraction
 */
internal class Tag(val id: ULong, val value: Value) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.writeArgument(Major.TAG, id)
        value.encode(into)
    }

    internal companion object {
        fun decode(buffer: SdkBufferedSource, depth: Int = 0): Tag {
            val id = decodeArgument(buffer)

            val value: Value = when (id) {
                TagId.TIMESTAMP.value -> Timestamp.decode(buffer)
                TagId.BIG_NUM.value -> BigNum.decode(buffer, depth)
                TagId.NEG_BIG_NUM.value -> NegBigNum.decode(buffer, depth)
                TagId.DECIMAL_FRACTION.value -> DecimalFraction.decode(buffer, depth)
                else -> throw DeserializationException("Unsupported tag ID $id")
            }

            return Tag(id, value)
        }
    }
}

/**
 * Represents a CBOR timestamp, a [Tag] with ID 1.
 * The tagged value is a number representing the number of seconds since epoch.
 * Note: this number may be an unsigned integer, negative integer, or floating point number.
 * @param value the [Instant] that this CBOR timestamp represents
 */
internal class Timestamp(val value: Instant) : Value {
    override fun encode(into: SdkBufferedSink) = into.writeTimestamp(value)

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): Timestamp = Timestamp(decodeInstant(buffer))
    }
}

internal fun SdkBufferedSink.writeTimestamp(value: Instant) {
    writeArgument(Major.TAG, TagId.TIMESTAMP.value)
    writeFloat64(value.epochMilliseconds / 1000.toDouble())
}

internal fun decodeInstant(buffer: SdkBufferedSource): Instant {
    val major = peekMajor(buffer)
    val minor = peekMinorByte(buffer)

    return when (major) {
        Major.U_INT -> {
            val timestamp = decodeUInt(buffer).toLong()
            Instant.fromEpochSeconds(timestamp)
        }
        Major.NEG_INT -> {
            val negativeTimestamp: Long = -(decodeNegInt(buffer).toLong())
            Instant.fromEpochSeconds(negativeTimestamp)
        }
        Major.TYPE_7 -> {
            val doubleTimestamp: Double = when (minor) {
                Minor.FLOAT16.value -> decodeFloat16(buffer).toDouble()
                Minor.FLOAT32.value -> decodeFloat32(buffer).toDouble()
                Minor.FLOAT64.value -> decodeFloat64(buffer)
                else -> throw DeserializationException("Unexpected minor type $minor for CBOR floating point timestamp, expected ${Minor.FLOAT16}, ${Minor.FLOAT32}, or ${Minor.FLOAT64}.")
            }
            Instant.fromEpochMilliseconds((doubleTimestamp * 1000).toLong())
        }
        else -> throw DeserializationException("Unexpected major type $major for CBOR Timestamp. Expected ${Major.U_INT}, ${Major.NEG_INT}, or ${Major.TYPE_7}.")
    }
}

/**
 * Represents a CBOR bignum, a [Tag] with ID 2.
 * @param value the [BigInteger] that this CBOR bignum represents.
 */
internal class BigNum(val value: BigInteger) : Value {
    override fun encode(into: SdkBufferedSink) = into.writeBigNum(value)

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource, depth: Int = 0): BigNum = BigNum(decodeBigNum(buffer, depth))
    }
}

internal fun SdkBufferedSink.writeBigNum(value: BigInteger) {
    writeArgument(Major.TAG, TagId.BIG_NUM.value)
    writeByteString(value.toByteArray())
}

internal fun decodeBigNum(buffer: SdkBufferedSource, depth: Int = 0): BigInteger = BigInteger(decodeByteStringValue(buffer, depth))

/**
 * Represents a CBOR negative bignum, a [Tag] with ID 3.
 * @param value the [BigInteger] that this negative CBOR bignum represents.
 */
internal class NegBigNum(val value: BigInteger) : Value {
    override fun encode(into: SdkBufferedSink) = into.writeNegBigNum(value)

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource, depth: Int = 0): NegBigNum = NegBigNum(decodeNegBigNum(buffer, depth))
    }
}

internal fun SdkBufferedSink.writeNegBigNum(value: BigInteger) {
    val magnitude = NEGATIVE_ONE - value
    val twosComplement = magnitude.toByteArray()
    val bytes = if (twosComplement.size > 1 && twosComplement[0] == 0.toByte()) {
        twosComplement.copyOfRange(1, twosComplement.size)
    } else {
        twosComplement
    }
    writeArgument(Major.TAG, TagId.NEG_BIG_NUM.value)
    writeByteString(bytes)
}

internal fun decodeNegBigNum(buffer: SdkBufferedSource, depth: Int = 0): BigInteger {
    val bytes = decodeByteStringValue(buffer, depth)

    val magnitude = BigInteger(byteArrayOf(0) + bytes)
    return NEGATIVE_ONE - magnitude
}

/**
 * Represents a CBOR decimal fraction, a [Tag] with ID 4.
 * @param value the [BigDecimal] that this decimal fraction represents.
 */
internal class DecimalFraction(val value: BigDecimal) : Value {
    override fun encode(into: SdkBufferedSink) = into.writeDecimalFraction(value)

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource, depth: Int = 0): DecimalFraction = DecimalFraction(decodeDecimalFraction(buffer, depth))
    }
}

internal fun SdkBufferedSink.writeDecimalFraction(value: BigDecimal) {
    val mantissaString = value.mantissa.toString()
    val isNegative = mantissaString.startsWith('-')
    val mantissaDigits = if (isNegative) mantissaString.length - 1 else mantissaString.length
    val cborExponent = value.exponent - (mantissaDigits - 1)

    writeArgument(Major.TAG, TagId.DECIMAL_FRACTION.value)
    writeArgument(Major.LIST, 2uL)

    if (cborExponent < 0) {
        writeNegInt(cborExponent.absoluteValue.toULong())
    } else {
        writeUInt(cborExponent.toULong())
    }

    if (value.mantissa > MIN_LONG_AS_BIG_INTEGER && value.mantissa < MAX_LONG_AS_BIG_INTEGER) {
        val mantissa = value.mantissa.toLong()
        if (isNegative) {
            writeNegInt(mantissa.absoluteValue.toULong())
        } else {
            writeUInt(mantissa.toULong())
        }
    } else {
        if (isNegative) {
            writeNegBigNum(value.mantissa)
        } else {
            writeBigNum(value.mantissa)
        }
    }
}

internal fun decodeDecimalFraction(buffer: SdkBufferedSource, depth: Int = 0): BigDecimal {
    val list = decodeListValue(buffer, depth)
    check(list.size == 2) { "Expected array of length 2 for decimal fraction, got ${list.size}" }

    val (exponentValue, mantissaValue) = list

    val mantissa = when (mantissaValue) {
        is UInt -> BigInteger(mantissaValue.value.toString())
        is NegInt -> BigInteger("-" + mantissaValue.value.toString())
        is Tag -> when (mantissaValue.value) {
            is NegBigNum -> mantissaValue.value.value
            is BigNum -> mantissaValue.value.value
            else -> throw DeserializationException("Expected BigNum or NegBigNum for CBOR tagged decimal fraction mantissa, got ${mantissaValue.id}")
        }
        else -> throw DeserializationException("Expected UInt, NegInt, or Tag for CBOR decimal fraction mantissa, got $mantissaValue")
    }

    val cborExponent = when (exponentValue) {
        is UInt -> exponentValue.value.toInt()
        is NegInt -> -exponentValue.value.toInt()
        else -> throw DeserializationException("Expected integer for CBOR decimal fraction exponent value, got $exponentValue.")
    }

    val mantissaString = mantissa.toString()
    val mantissaDigits = if (mantissaString.startsWith('-')) mantissaString.length - 1 else mantissaString.length
    return try {
        BigDecimal(mantissa, cborExponent + (mantissaDigits - 1))
    } catch (iae: IllegalArgumentException) {
        throw DeserializationException("Cannot deserialize unsupported BigDecimal value", iae)
    }
}
