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
import aws.smithy.kotlin.runtime.serde.cbor.encodeArgument
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlin.math.absoluteValue

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
        into.write(encodeArgument(Major.TAG, id))
        value.encode(into)
    }

    internal companion object {
        fun decode(buffer: SdkBufferedSource): Tag {
            val id = decodeArgument(buffer)

            val value: Value = when (id) {
                TagId.TIMESTAMP.value -> Timestamp.decode(buffer)
                TagId.BIG_NUM.value -> BigNum.decode(buffer)
                TagId.NEG_BIG_NUM.value -> NegBigNum.decode(buffer)
                TagId.DECIMAL_FRACTION.value -> DecimalFraction.decode(buffer)
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
    override fun encode(into: SdkBufferedSink) = Tag(1u, Float64(value.epochMilliseconds / 1000.toDouble())).encode(into)

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): Timestamp {
            val major = peekMajor(buffer)
            val minor = peekMinorByte(buffer)

            val instant: Instant = when (major) {
                Major.U_INT -> {
                    val timestamp = UInt.decode(buffer).value.toLong()
                    Instant.fromEpochSeconds(timestamp)
                }
                Major.NEG_INT -> {
                    val negativeTimestamp: Long = NegInt.decode(buffer).value.toLong()
                    Instant.fromEpochSeconds(negativeTimestamp)
                }
                Major.TYPE_7 -> {
                    val doubleTimestamp: Double = when (minor) {
                        Minor.FLOAT16.value -> Float16.decode(buffer).value.toDouble()
                        Minor.FLOAT32.value -> Float32.decode(buffer).value.toDouble()
                        Minor.FLOAT64.value -> Float64.decode(buffer).value
                        else -> throw DeserializationException("Unexpected minor type $minor for CBOR floating point timestamp, expected ${Minor.FLOAT16}, ${Minor.FLOAT32}, or ${Minor.FLOAT64}.")
                    }
                    Instant.fromEpochMilliseconds((doubleTimestamp * 1000).toLong())
                }
                else -> throw DeserializationException("Unexpected major type $major for CBOR Timestamp. Expected ${Major.U_INT}, ${Major.NEG_INT}, or ${Major.TYPE_7}.")
            }

            return Timestamp(instant)
        }
    }
}

/**
 * Represents a CBOR bignum, a [Tag] with ID 2.
 * @param value the [BigInteger] that this CBOR bignum represents.
 */
internal class BigNum(val value: BigInteger) : Value {
    override fun encode(into: SdkBufferedSink) = Tag(2u, ByteString(value.toByteArray())).encode(into)

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): BigNum {
            val bytes = ByteString.decode(buffer).value
            return BigNum(BigInteger(bytes))
        }
    }
}

/**
 * Represents a CBOR negative bignum, a [Tag] with ID 3.
 * @param value the [BigInteger] that this negative CBOR bignum represents.
 */
internal class NegBigNum(val value: BigInteger) : Value {
    override fun encode(into: SdkBufferedSink) {
        val bytes = (value - BigInteger("1")).toByteArray()
        Tag(3u, ByteString(bytes)).encode(into)
    }

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): NegBigNum {
            val bytes = ByteString.decode(buffer).value

            // note: CBOR encoding implies (-1 - $value), add one to get the real value.
            val bigInteger = BigInteger(bytes) + BigInteger("1")
            return NegBigNum(bigInteger)
        }
    }
}

/**
 * Represents a CBOR decimal fraction, a [Tag] with ID 4.
 * @param value the [BigDecimal] that this decimal fraction represents.
 */
internal class DecimalFraction(val value: BigDecimal) : Value {
    override fun encode(into: SdkBufferedSink) {
        val str = value.toPlainString()
        val dotIndex = str.indexOf('.').takeIf { it != -1 } ?: str.lastIndex
        val exponentValue = (dotIndex - str.length + 1).toLong()
        val exponent = if (exponentValue < 0) {
            NegInt(exponentValue.absoluteValue.toULong())
        } else {
            UInt(exponentValue.toULong())
        }

        val mantissaStr = str.replace(".", "")
        // Check if the mantissa can be represented as a UInt without overflowing.
        // If not, it will be encoded as a Bignum.
        val mantissa: Value = try {
            if (mantissaStr.startsWith("-")) {
                NegInt(mantissaStr.toLong().absoluteValue.toULong())
            } else {
                UInt(mantissaStr.toULong())
            }
        } catch (e: NumberFormatException) {
            val bigMantissa = BigInteger(mantissaStr)
            if (mantissaStr.startsWith("-")) {
                NegBigNum(bigMantissa)
            } else {
                BigNum(bigMantissa)
            }
        }

        Tag(TagId.DECIMAL_FRACTION.value, List(listOf(exponent, mantissa))).encode(into)
    }

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): DecimalFraction {
            val list = List.decode(buffer).value
            check(list.size == 2) { "Expected array of length 2 for decimal fraction, got ${list.size}" }

            val (exponent, mantissa) = list

            val sb = StringBuilder()

            // Append mantissa
            sb.append(
                when (mantissa) {
                    is UInt -> mantissa.value.toString()
                    is NegInt -> "-${mantissa.value}"
                    is Tag -> when (mantissa.value) {
                        is NegBigNum -> mantissa.value.value.toString()
                        is BigNum -> mantissa.value.value.toString()
                        else -> throw DeserializationException("Expected BigNum or NegBigNum for CBOR tagged decimal fraction mantissa, got ${mantissa.id}")
                    }
                    else -> throw DeserializationException("Expected UInt, NegInt, or Tag for CBOR decimal fraction mantissa, got $mantissa")
                },
            )

            when (exponent) {
                is UInt -> { // Positive exponent, suffix with zeroes
                    sb.append("0".repeat(exponent.value.toInt()))
                    sb.append(".")
                }
                is NegInt -> { // Negative exponent, prefix with zeroes if necessary
                    val exponentValue = exponent.value.toInt().absoluteValue
                    val insertIndex = if (sb[0] == '-') 1 else 0
                    if (exponentValue > sb.length - insertIndex) {
                        sb.insert(insertIndex, "0".repeat(exponentValue - sb.length + insertIndex))
                        sb.insert(insertIndex, '.')
                    } else {
                        sb.insert(sb.length - exponentValue, '.')
                    }
                }
                else -> throw DeserializationException("Expected integer for CBOR decimal fraction exponent value, got $exponent.")
            }

            return DecimalFraction(BigDecimal(sb.toString()))
        }
    }
}
