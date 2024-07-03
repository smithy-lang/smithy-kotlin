/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.SerializationException
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlin.math.absoluteValue

internal object Cbor {
    /**
     * Represents an encodable / decodable CBOR value.
     */
    internal interface Value {
        /**
         * The bytes representing the encoded value
         */
        fun encode(): ByteArray

        companion object {
            fun decode(buffer: SdkBufferedSource): Value {
                val major = peekMajor(buffer)
                val minor = peekMinorByte(buffer)

                return when (major) {
                    Major.U_INT -> Encoding.UInt.decode(buffer)
                    Major.NEG_INT -> Encoding.NegInt.decode(buffer)
                    Major.BYTE_STRING -> Encoding.ByteString.decode(buffer)
                    Major.STRING -> Encoding.String.decode(buffer)
                    Major.LIST -> {
                        return if (minor == Minor.INDEFINITE.value) {
                            Encoding.IndefiniteList.decode(buffer)
                        } else {
                            Encoding.List.decode(buffer)
                        }
                    }
                    Major.MAP -> {
                        if (minor == Minor.INDEFINITE.value) {
                            Encoding.IndefiniteMap.decode(buffer)
                        } else {
                            Encoding.Map.decode(buffer)
                        }
                    }
                    Major.TAG -> Encoding.Tag.decode(buffer)
                    Major.TYPE_7 -> {
                        when (minor) {
                            Minor.TRUE.value -> Encoding.Boolean.decode(buffer)
                            Minor.FALSE.value -> Encoding.Boolean.decode(buffer)
                            Minor.NULL.value -> Encoding.Null.decode(buffer)
                            Minor.UNDEFINED.value -> Encoding.Null.decode(buffer)
                            Minor.FLOAT16.value -> Encoding.Float16.decode(buffer)
                            Minor.FLOAT32.value -> Encoding.Float32.decode(buffer)
                            Minor.FLOAT64.value -> Encoding.Float64.decode(buffer)
                            Minor.INDEFINITE.value -> Encoding.IndefiniteBreak.decode(buffer)
                            else -> throw DeserializationException("Unexpected type 7 minor value $minor")
                        }
                    }
                }
            }
        }
    }

    internal object Encoding {
        /**
         * Represents a CBOR unsigned integer (major type 0) in the range [0, 2^64-1].
         * @param value The [ULong] value which this unsigned integer represents.
         */
        internal class UInt(val value: ULong) : Value {
            override fun encode(): ByteArray = encodeArgument(Major.U_INT, value)

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
            override fun encode(): ByteArray = encodeArgument(Major.NEG_INT, value - 1u)

            internal companion object {
                fun decode(buffer: SdkBufferedSource): NegInt {
                    val argument: ULong = decodeArgument(buffer)
                    return NegInt(argument + 1u)
                }
            }
        }

        /**
         * Represents a CBOR byte string (major type 2).
         * @param value The [ByteArray] which this CBOR byte string represents.
         */
        internal class ByteString(val value: ByteArray) : Value {
            override fun encode(): ByteArray {
                val head = encodeArgument(Major.BYTE_STRING, value.size.toULong())
                return byteArrayOf(*head, *value)
            }

            internal companion object {
                fun decode(buffer: SdkBufferedSource): ByteString =
                    if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
                        val list = IndefiniteList.decode(buffer).value

                        val tempBuffer = SdkBuffer()
                        list.forEach {
                            tempBuffer.write((it as ByteString).value)
                        }

                        ByteString(tempBuffer.readByteArray())
                    } else {
                        val length = decodeArgument(buffer).toInt()

                        val bytes = SdkBuffer().use {
                            buffer.readFully(it, length.toLong())
                            it.readByteArray()
                        }

                        ByteString(bytes)
                    }
            }
        }

        /**
         * Represents a CBOR string (major type 3) encoded as a UTF-8 byte array.
         * @param value The [String] which this CBOR string represents.
         */
        internal class String(val value: kotlin.String) : Value {
            override fun encode(): ByteArray {
                val head = encodeArgument(Major.STRING, value.length.toULong())
                return byteArrayOf(*head, *value.encodeToByteArray())
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
         * Represents a CBOR list (major type 4).
         * @param value the [kotlin.collections.List<Value>] represented by this CBOR list.
         */
        internal class List(val value: kotlin.collections.List<Value>) : Value {
            override fun encode(): ByteArray {
                val byteBuffer = SdkBuffer()

                byteBuffer.write(encodeArgument(Major.LIST, value.size.toULong()))

                value.forEach { v ->
                    byteBuffer.write(v.encode())
                }

                return byteBuffer.readByteArray()
            }

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): List {
                    val length = decodeArgument(buffer).toInt()
                    val valuesList = mutableListOf<Value>()

                    for (i in 0 until length) {
                        valuesList.add(Value.decode(buffer))
                    }

                    return List(valuesList)
                }
            }
        }

        /**
         * Represents a CBOR list with an indefinite length (major type 4, minor type 31).
         * @param value The optional [MutableList] that this CBOR indefinite list represents. This value is mainly
         * used for storing a list of decoded values.
         *
         * Note: `encode` will just *begin* encoding the list, callers are expected to:
         * - call `encode` for each [Value] in the list
         * - end the list by sending an [IndefiniteBreak]
         *
         * `decode` will consume list values until an [IndefiniteBreak] is encountered.
         */
        internal class IndefiniteList(val value: MutableList<Value> = mutableListOf()) : Value {
            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.LIST, Minor.INDEFINITE))

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): IndefiniteList {
                    buffer.readByte() // discard head

                    val list = mutableListOf<Value>()

                    while (!buffer.nextValueIsIndefiniteBreak) {
                        list.add(Value.decode(buffer))
                    }

                    IndefiniteBreak.decode(buffer)
                    return IndefiniteList(list)
                }
            }
        }

        /**
         * Represents a CBOR map (major type 5).
         * @param value The [kotlin.collections.Map] that this CBOR map represents.
         */
        internal class Map(val value: kotlin.collections.Map<Value, Value>) : Value {
            override fun encode(): ByteArray {
                val byteBuffer = SdkBuffer()
                byteBuffer.write(encodeArgument(Major.MAP, value.size.toULong()))

                value.forEach { (key, v) ->
                    byteBuffer.write(key.encode())
                    byteBuffer.write(v.encode())
                }

                return byteBuffer.readByteArray()
            }

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): Map {
                    val valueMap = mutableMapOf<Value, Value>()
                    val length = decodeArgument(buffer).toInt()

                    for (i in 0 until length) {
                        val key = Value.decode(buffer)
                        val value = Value.decode(buffer)
                        valueMap[key] = value
                    }

                    return Map(valueMap)
                }
            }
        }

        /**
         * Represents a CBOR map with indefinite length (major type 5, minor type 31).
         * @param value The optional [MutableMap] that this CBOR indefinite map represents. This value is mainly
         * used for storing the decoded entries of the map.
         *
         * Note: `encode` will just *begin* encoding the map, callers are expected to:
         * - call `encode` for each [String]/[Value] value pair in the map
         * - end the map by sending an [IndefiniteBreak]
         *
         * `decode` will consume map entries until an [IndefiniteBreak] is encountered.
         */
        internal class IndefiniteMap(val value: MutableMap<String, Value> = mutableMapOf()) : Value {
            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.MAP, Minor.INDEFINITE))

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): IndefiniteMap {
                    buffer.readByte() // discard head byte
                    val valueMap = mutableMapOf<String, Value>()

                    while (!buffer.nextValueIsIndefiniteBreak) {
                        val key = String.decode(buffer)
                        val value = Value.decode(buffer)
                        valueMap[key] = value
                    }

                    IndefiniteBreak.decode(buffer)
                    return IndefiniteMap(valueMap)
                }
            }
        }

        /**
         * Represents a tagged CBOR [Value] (major type 6). The minor type describes the contents of the tagged value:
         * - 1 -> Timestamp (encoded as epoch seconds)
         * - 2 -> Unsigned bignum
         * - 3 -> Negative bignum
         * - 4 -> Decimal fraction
         */
        internal class Tag(val id: ULong, val value: Value) : Value {
            override fun encode(): ByteArray = byteArrayOf(*encodeArgument(Major.TAG, id), *value.encode())

            internal companion object {
                fun decode(buffer: SdkBufferedSource): Tag {
                    val id = peekMinorByte(buffer).toULong()

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
         * Represents a CBOR boolean (major type 7). The minor type is 5 for false and 6 for true.
         * @param value the [kotlin.Boolean] this CBOR boolean represents.
         */
        internal class Boolean(val value: kotlin.Boolean) : Value {
            override fun encode(): ByteArray = byteArrayOf(
                when (value) {
                    false -> encodeMajorMinor(Major.TYPE_7, Minor.FALSE)
                    true -> encodeMajorMinor(Major.TYPE_7, Minor.TRUE)
                },
            )

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): Boolean {
                    peekMajor(buffer).also {
                        check(it == Major.TYPE_7) { "Expected ${Major.TYPE_7} for CBOR boolean, got $it" }
                    }

                    return when (val minor = peekMinorByte(buffer)) {
                        Minor.FALSE.value -> Boolean(false)
                        Minor.TRUE.value -> Boolean(true)
                        else -> throw DeserializationException("Unknown minor argument $minor for Boolean")
                    }.also {
                        buffer.readByte()
                    }
                }
            }
        }

        /**
         * Represents a CBOR null value (major type 7, minor type 7).
         */
        internal class Null : Value {
            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.TYPE_7, Minor.NULL))

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): Null {
                    val major = peekMajor(buffer)
                    check(major == Major.TYPE_7) { "Expected ${Major.TYPE_7} for CBOR null, got $major" }

                    val minor = peekMinorByte(buffer)
                    check(minor == Minor.NULL.value || minor == Minor.UNDEFINED.value) { "Expected ${Minor.NULL} or ${Minor.UNDEFINED} for CBOR null, got $minor" }

                    buffer.readByte() // consume the byte
                    return Null()
                }
            }
        }

        /**
         * Represents a CBOR 16-bit float (major type 7, minor type 25).
         * Note: This CBOR type can only be *decoded*, it will never be encoded.
         * @param value the [Float] that this CBOR 16-bit float represents.
         */
        internal class Float16(val value: Float) : Value {
            override fun encode(): ByteArray = throw SerializationException("Encoding of CBOR 16-bit floats is not supported")

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
            override fun encode(): ByteArray {
                val bits: Int = value.toRawBits()

                val bytes = (24 downTo 0 step 8).map { shiftAmount ->
                    (bits shr shiftAmount and 0xff).toByte()
                }.toByteArray()

                return byteArrayOf(encodeMajorMinor(Major.TYPE_7, Minor.FLOAT32), *bytes)
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
            override fun encode(): ByteArray {
                val bits: Long = value.toRawBits()
                val bytes = (56 downTo 0 step 8).map { shiftAmount ->
                    (bits shr shiftAmount and 0xff).toByte()
                }.toByteArray()

                return byteArrayOf(encodeMajorMinor(Major.TYPE_7, Minor.FLOAT64), *bytes)
            }

            internal companion object {
                fun decode(buffer: SdkBufferedSource): Float64 {
                    buffer.readByte() // discard head byte
                    val bytes = buffer.readByteArray(8)
                    return Float64(Double.fromBits(bytes.toULong().toLong()))
                }
            }
        }

        internal class Timestamp(val value: Instant) : Value {
            override fun encode(): ByteArray = Tag(1u, Float64(value.epochMilliseconds / 1000.toDouble())).encode()

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): Timestamp {
                    val tagId = decodeArgument(buffer).toInt()
                    check(tagId == 1) { "Expected tag ID 1 for CBOR timestamp, got $tagId" }

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
         * Represents a CBOR bignum (tagged value with ID 2).
         * @param value the [BigInteger] that this CBOR bignum represents.
         */
        internal class BigNum(val value: BigInteger) : Value {
            override fun encode(): ByteArray = Tag(2u, ByteString(value.asBytes())).encode()

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): BigNum {
                    val tagId = decodeArgument(buffer).toInt()
                    check(tagId == 2) { "Expected tag ID 2 for CBOR bignum, got $tagId" }

                    val bytes = ByteString.decode(buffer).value
                    return BigNum(bytes.toBigInteger())
                }
            }
        }

        /**
         * Represents a CBOR negative bignum (tagged value with ID 3).
         * @param value the [BigInteger] that this negative CBOR bignum represents.
         * Values will be properly encoded / decoded according to the CBOR specification (-1 minus $value)
         */
        internal class NegBigNum(val value: BigInteger) : Value {
            override fun encode(): ByteArray {
                val bytes = value.minusOne().asBytes()
                return Tag(3u, ByteString(bytes)).encode()
            }

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): NegBigNum {
                    val tagId = decodeArgument(buffer).toInt()
                    check(tagId == 3) { "Expected tag ID 3 for CBOR negative bignum, got $tagId" }

                    val bytes = ByteString.decode(buffer).value

                    // note: encoding implies (-1 - $value).
                    // add one to get the real value. prepend with minus to correctly set up the negative BigInteger
                    val bigInteger = BigInteger("-" + bytes.toBigInteger().plusOne().toString())
                    return NegBigNum(bigInteger)
                }
            }
        }

        /**
         * Represents a CBOR decimal fraction (tagged value with ID 4).
         * @param value the [BigDecimal] that this decimal fraction represents.
         */
        internal class DecimalFraction(val value: BigDecimal) : Value {
            override fun encode(): ByteArray {
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

                return Tag(TagId.DECIMAL_FRACTION.value, List(listOf(exponent, mantissa))).encode()
            }

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): DecimalFraction {
                    peekMajor(buffer).also {
                        check(it == Major.TAG) { "Expected ${Major.TAG} for CBOR decimal fraction, got $it" }
                    }

                    val tagId = decodeArgument(buffer)
                    check(tagId == TagId.DECIMAL_FRACTION.value) { "Expected tag ID ${TagId.DECIMAL_FRACTION.value} for CBOR decimal fraction, got $tagId" }

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

        /**
         * Represents the "break" stop-code for lists/maps with an indefinite length (major type 7, minor type 31).
         */
        internal object IndefiniteBreak : Value {
            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.TYPE_7, Minor.INDEFINITE))
            internal fun decode(buffer: SdkBufferedSource): IndefiniteBreak {
                val major = peekMajor(buffer)
                check(major == Major.TYPE_7) { "Expected CBOR indefinite break stop-code to be major ${Major.TYPE_7}, got $major." }

                val minor = peekMinorByte(buffer)
                check(minor == Minor.INDEFINITE.value) { "Expected CBOR indefinite break stop-code to be minor ${Minor.INDEFINITE}, got $minor." }

                buffer.readByte() // discard major/minor
                return IndefiniteBreak
            }
        }
    }
}
