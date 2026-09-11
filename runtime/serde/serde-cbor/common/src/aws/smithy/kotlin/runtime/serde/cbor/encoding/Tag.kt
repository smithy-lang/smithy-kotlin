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
import aws.smithy.kotlin.runtime.serde.DeserializationRecursionException
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
 * Write a CBOR timestamp, a tag with ID 1.
 * The tagged value is a number representing the number of seconds since epoch.
 */
internal fun SdkBufferedSink.writeTimestamp(value: Instant) {
    writeArgument(Major.TAG, TagId.TIMESTAMP.value)
    writeFloat64(value.epochMilliseconds / 1000.toDouble())
}

/**
 * Decode the value of a CBOR timestamp tag as an [Instant].
 */
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
 * Write a CBOR bignum, a tag with ID 2.
 */
internal fun SdkBufferedSink.writeBigNum(value: BigInteger) {
    writeArgument(Major.TAG, TagId.BIG_NUM.value)
    writeByteString(value.toByteArray())
}

internal fun decodeBigNum(buffer: SdkBufferedSource, depth: Int = 0): BigInteger = BigInteger(decodeByteStringValue(buffer, depth))

/**
 * Write a CBOR negative bignum, a tag with ID 3.
 */
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
 * Write a CBOR decimal fraction, a tag with ID 4.
 */
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
    DeserializationRecursionException.assertDepth(depth)

    val length = decodeArgument(buffer).toLong()
    check(length == 2L) { "Expected array of length 2 for decimal fraction, got $length" }

    val cborExponent = when (val major = peekMajor(buffer)) {
        Major.U_INT -> decodeUInt(buffer).toInt()
        Major.NEG_INT -> -decodeNegInt(buffer).toInt()
        else -> throw DeserializationException("Expected integer for CBOR decimal fraction exponent value, got $major.")
    }

    val mantissa = when (val major = peekMajor(buffer)) {
        Major.U_INT -> BigInteger(decodeUInt(buffer).toString())
        Major.NEG_INT -> BigInteger("-" + decodeNegInt(buffer).toString())
        Major.TAG -> when (val id = decodeArgument(buffer)) {
            TagId.BIG_NUM.value -> decodeBigNum(buffer, depth + 1)
            TagId.NEG_BIG_NUM.value -> decodeNegBigNum(buffer, depth + 1)
            else -> throw DeserializationException("Expected BigNum or NegBigNum tag for CBOR decimal fraction mantissa, got tag $id")
        }
        else -> throw DeserializationException("Expected integer or bignum for CBOR decimal fraction mantissa, got major type $major")
    }

    val mantissaString = mantissa.toString()
    val mantissaDigits = if (mantissaString.startsWith('-')) mantissaString.length - 1 else mantissaString.length
    return try {
        BigDecimal(mantissa, cborExponent + (mantissaDigits - 1))
    } catch (iae: IllegalArgumentException) {
        throw DeserializationException("Cannot deserialize unsupported BigDecimal value", iae)
    }
}
