/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.io.use
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlin.math.absoluteValue

/**
 * Encode and write a [Cbor.Value] to this [SdkBuffer]
 */
internal fun SdkBuffer.write(value: Cbor.Value) = write(value.encode())

internal object Cbor {
    /**
     * Represents an encodable / decodable CBOR value.
     */
    internal interface Value {
        /**
         * The bytes representing the encoded value
         */
        fun encode(): ByteArray
    }

    internal object Encoding {
        /**
         * Represents a CBOR unsigned integer (major type 0) in the range [0, 2^64-1].
         * @param value The [ULong] value which this unsigned integer represents.
         */
        internal class UInt(val value: ULong) : Value {
            override fun encode(): ByteArray = encodeArgument(Major.U_INT, value)
            internal companion object {
                fun decode(buffer: SdkBufferedSource): UInt {
                    val argument = deserializeArgument(buffer)
                    return UInt(argument)
                }
            }
        }

        /**
         * Represents a CBOR negative integer (major type 1) in the range [-2^64, -1].
         * @param value The [ULong] value which this unsigned integer represents.
         *
         * Values will be properly encoded / decoded according to the CBOR specification (-1 minus $value)
         */
        internal class NegInt(val value: Long) : Value {
            override fun encode(): ByteArray = encodeArgument(Major.NEG_INT, (value.absoluteValue - 1).toULong())

            internal companion object {
                fun decode(buffer: SdkBufferedSource): NegInt {
                    val argument: ULong = deserializeArgument(buffer)
                    return NegInt(0 - (argument + 1u).toLong())
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
                fun decode(buffer: SdkBufferedSource): ByteString {
                    val minor = peekMinorByte(buffer)

                    return if (minor == Minor.INDEFINITE.value) {
                        val list = IndefiniteList.decode(buffer).value

                        val tempBuffer = SdkBuffer()
                        list.forEach {
                            tempBuffer.write((it as ByteString).value)
                        }

                        ByteString(tempBuffer.readByteArray())
                    } else {
                        val length = deserializeArgument(buffer).toInt()
                        val bytes = ByteArray(length)

                        if (length > 0) {
                            val rc = buffer.read(bytes)
                            check(rc == length) { "Unexpected end of CBOR byte string: expected $length bytes, got $rc." }
                        }

                        ByteString(bytes)
                    }
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
                fun decode(buffer: SdkBufferedSource): String {
                    val minor = peekMinorByte(buffer)

                    return if (minor == Minor.INDEFINITE.value) {
                        val list = IndefiniteList.decode(buffer).value

                        val sb = StringBuilder()
                        list.forEach {
                            sb.append((it as String).value)
                        }

                        val str = sb.toString()
                        String(str)
                    } else {
                        val length = deserializeArgument(buffer).toInt()
                        val bytes = ByteArray(length)

                        if (length > 0) {
                            val rc = buffer.read(bytes)
                            check(rc == length) { "Unexpected end of CBOR string: expected $length bytes, got $rc." }
                        }

                        val str = bytes.decodeToString()
                        String(str)
                    }
                }
            }
        }

        // Represents a CBOR list (major type 4).
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
                    val length = deserializeArgument(buffer).toInt()
                    val values = mutableListOf<Value>()

                    for (i in 0 until length) {
                        values.add(decodeNextValue(buffer))
                    }

                    return List(values)
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
                    var peekedMajor = peekMajor(buffer)
                    var peekedMinor = peekMinorByte(buffer)

                    while (true) {
                        if (peekedMajor == Major.TYPE_7 && peekedMinor == Minor.INDEFINITE.value) {
                            IndefiniteBreak.decode(buffer)
                            break
                        } else {
                            list.add(decodeNextValue(buffer))
                            peekedMajor = peekMajor(buffer)
                            peekedMinor = peekMinorByte(buffer)
                        }
                    }

                    return IndefiniteList(list)
                }
            }
        }

        /**
         * Represents a CBOR map (major type 5).
         * @param value The [kotlin.collections.Map] that this CBOR map represents.
         */
        internal class Map(val value: kotlin.collections.Map<String, Value>) : Value {
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
                    val valueMap = mutableMapOf<String, Value>()
                    val length = deserializeArgument(buffer).toInt()

                    for (i in 0 until length) {
                        val key = String.decode(buffer)
                        val value = decodeNextValue(buffer)
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

                    var peekedMajor = peekMajor(buffer)
                    var peekedMinor = peekMinorByte(buffer)
                    while (true) {
                        if (peekedMajor == Major.TYPE_7 && peekedMinor == Minor.INDEFINITE.value) {
                            IndefiniteBreak.decode(buffer)
                            break
                        } else {
                            val key = String.decode(buffer)
                            val value = decodeNextValue(buffer)
                            valueMap[key] = value
                            peekedMajor = peekMajor(buffer)
                            peekedMinor = peekMinorByte(buffer)
                        }
                    }

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
                fun decode(buffer: SdkBufferedSource): Tag = when (val id = peekMinorByte(buffer).toUInt()) {
                    1u -> { Tag(id.toULong(), Timestamp.decode(buffer)) }
                    2u -> { Tag(id.toULong(), BigNum.decode(buffer)) }
                    3u -> { Tag(id.toULong(), NegBigNum.decode(buffer)) }
                    4u -> { Tag(id.toULong(), DecimalFraction.decode(buffer)) }
                    else -> throw DeserializationException("Unknown tag ID $id")
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
                    val major = peekMajor(buffer)
                    check(major == Major.TYPE_7) { "Expected ${Major.TYPE_7} for CBOR boolean, got $major" }

                    return when (val minor = peekMinorByte(buffer)) {
                        Minor.FALSE.value -> { Boolean(false) }
                        Minor.TRUE.value -> { Boolean(true) }
                        else -> throw DeserializationException("Unknown minor argument $minor for Boolean.")
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
            override fun encode(): ByteArray = TODO("Encoding for CBOR 16-bit floats is not supported")

            internal companion object {
                fun decode(buffer: SdkBufferedSource): Float16 {
                    buffer.readByte() // discard head byte
                    val bytes = buffer.readByteArray(2)

                    val float16Bits: Int = (
                        ((bytes[0].toInt() and 0xff) shl 8) or
                            (bytes[1].toInt() and 0xff)
                        )

                    val sign = (float16Bits and (0x1 shl 15)) shl 16 // top bit
                    val exponent = (float16Bits and (0x1f shl 10)) shr 10 // next 5 bits
                    val fraction = (float16Bits and 0x3ff) shl 13 // remaining 10 bits

                    val float32: Int = if (exponent == 0x1f) { // Infinity / NaN
                        sign or (0xff shl 23) or fraction
                    } else if (exponent == 0) {
                        if (fraction == 0) {
                            sign
                        } else { // handle subnormal
                            var normalizedExponent: Int = -14 + 127
                            var normalizedFraction: Int = fraction
                            while (normalizedFraction and 0x800000 == 0) { // shift left until 24th bit of mantissa is '1'
                                normalizedFraction = normalizedFraction shl 1
                                normalizedExponent -= 1
                            }
                            normalizedFraction = normalizedFraction and 0x7fffff
                            sign or (normalizedExponent shl 23) or normalizedFraction
                        }
                    } else {
                        sign or ((exponent + 127 - 15) shl 23) or fraction
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
                val bits = value.toRawBits()
                return byteArrayOf(
                    encodeMajorMinor(Major.TYPE_7, Minor.FLOAT32),
                    (bits shr 24 and 0xff).toByte(),
                    (bits shr 16 and 0xff).toByte(),
                    (bits shr 8 and 0xff).toByte(),
                    (bits and 0xff).toByte(),
                )
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
                val bits = value.toRawBits()
                return byteArrayOf(
                    encodeMajorMinor(Major.TYPE_7, Minor.FLOAT64),
                    (bits shr 56 and 0xff).toByte(),
                    (bits shr 48 and 0xff).toByte(),
                    (bits shr 40 and 0xff).toByte(),
                    (bits shr 32 and 0xff).toByte(),
                    (bits shr 24 and 0xff).toByte(),
                    (bits shr 16 and 0xff).toByte(),
                    (bits shr 8 and 0xff).toByte(),
                    (bits and 0xff).toByte(),
                )
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
            override fun encode(): ByteArray = byteArrayOf(*Tag(1u, Float64(value.epochMilliseconds / 1000.toDouble())).encode())

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): Timestamp {
                    val tagId = deserializeArgument(buffer).toInt()
                    check(tagId == 1) { "Expected tag ID 1 for CBOR timestamp, got $tagId" }

                    val major = peekMajor(buffer)
                    val minor = peekMinorByte(buffer)

                    val instant: Instant = when (major) {
                        Major.U_INT -> {
                            val timestamp = UInt.decode(buffer).value.toLong() // note: possible truncation here because kotlin.time.Instant takes a Long, not a ULong
                            Instant.fromEpochSeconds(timestamp)
                        }
                        Major.NEG_INT -> {
                            val negativeTimestamp: Long = NegInt.decode(buffer).value // note: possible truncation here because kotlin.time.Instant takes a Long, not a ULong
                            Instant.fromEpochSeconds(negativeTimestamp)
                        }
                        Major.TYPE_7 -> {
                            val doubleTimestamp: Double = when (minor) {
                                Minor.FLOAT16.value -> { Float16.decode(buffer).value.toDouble() }
                                Minor.FLOAT32.value -> { Float32.decode(buffer).value.toDouble() }
                                Minor.FLOAT64.value -> { Float64.decode(buffer).value }
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
            override fun encode(): ByteArray = byteArrayOf(*Tag(2u, ByteString(value.asBytes())).encode())

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): BigNum {
                    val tagId = deserializeArgument(buffer).toInt()
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
                val subbed = value.minusOne()
                val bytes = subbed.asBytes()
                val tagged = Tag(3u, ByteString(bytes))
                return byteArrayOf(*tagged.encode())
            }

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): NegBigNum {
                    val tagId = deserializeArgument(buffer).toInt()
                    check(tagId == 3) { "Expected tag ID 3 for CBOR negative bignum, got $tagId" }

                    val bytes = ByteString.decode(buffer).value

                    // encoding implies (-1 - $value). add one to get the real value. prepend with minus to correctly set up the BigInteger
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

                val dotIndex = str
                    .indexOf('.')
                    .let { if (it == -1) str.lastIndex else it }

                val exponentValue = (dotIndex - str.length + 1).toLong()
                val exponent = if (exponentValue < 0) { NegInt(exponentValue) } else { UInt(exponentValue.toULong()) }

                val mantissaStr = str.replace(".", "")
                // Check if the mantissa can be represented as a UInt without overflowing.
                // If not, it will be sent as a Bignum.
                val mantissa: Value = try {
                    if (mantissaStr.startsWith("-")) {
                        NegInt(mantissaStr.toLong())
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

                return byteArrayOf(
                    *Tag(
                        4u,
                        List(listOf(exponent, mantissa)),
                    ).encode(),
                )
            }

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): DecimalFraction {
                    val major = peekMajor(buffer)
                    check(major == Major.TAG) { "Expected ${Major.TAG} for CBOR decimal fraction, got $major" }

                    val tagId = deserializeArgument(buffer)
                    check(tagId == 4uL) { "Expected tag ID 4 for CBOR decimal fraction, got $tagId" }

                    val array = List.decode(buffer)
                    check(array.value.size == 2) { "Expected array of length 2 for decimal fraction, got ${array.value.size}" }

                    val exponent = array.value[0]
                    val mantissa = array.value[1]

                    val sb = StringBuilder()

                    // append mantissa, prepend with '-' if negative.
                    when (mantissa) {
                        is UInt -> { sb.append(mantissa.value.toString()) }
                        is NegInt -> { sb.append(mantissa.value.toString()) }
                        is Tag -> when (mantissa.value) {
                            is NegBigNum -> { sb.append(mantissa.value.value.toString()) }
                            is BigNum -> { sb.append(mantissa.value.value.toString()) }
                            else -> throw DeserializationException("Expected negative bignum (id=3) or bignum (id=4) for CBOR tagged decimal fraction mantissa, got ${mantissa.id}.")
                        }
                        else -> throw DeserializationException("Expected integer or tagged value (bignum) for CBOR decimal fraction mantissa, got $mantissa.")
                    }

                    when (exponent) {
                        is UInt -> { // Positive exponent, suffix with zeroes
                            sb.append("0".repeat(exponent.value.toInt()))
                            sb.append(".")
                        }
                        is NegInt -> { // Negative exponent, prefix with zeroes if necessary
                            val exponentValue = exponent.value.toInt().absoluteValue
                            val insertIndex = if (sb[0] == '-') { 1 } else { 0 }
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
        internal class IndefiniteBreak : Value {
            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.TYPE_7, Minor.INDEFINITE))
            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): IndefiniteBreak {
                    val major = peekMajor(buffer)
                    check(major == Major.TYPE_7) { "Expected CBOR indefinite break stop-code to be major ${Major.TYPE_7}, got $major." }

                    val minor = peekMinorByte(buffer)
                    check(minor == Minor.INDEFINITE.value) { "Expected CBOR indefinite break stop-code to be minor ${Minor.INDEFINITE}, got $minor." }

                    buffer.readByte() // discard major/minor
                    return IndefiniteBreak()
                }
            }
        }
    }
}

// Encodes a major and minor type of CBOR value in a single byte
internal fun encodeMajorMinor(major: Major, minor: Minor): Byte = (major.value.toUInt() shl 5 or minor.value.toUInt()).toByte()

internal fun encodeArgument(major: Major, argument: ULong): ByteArray {
    if (argument < 24u) {
        // entire argument fits in the single head byte
        val head = ((major.ordinal shl 5).toULong() or argument).toByte()
        return byteArrayOf(head)
    } else if (argument < 0x100u) {
        // head + 1 byte
        val head = ((major.ordinal shl 5) or Minor.ARG_1.value.toInt()).toByte()
        return byteArrayOf(head, argument.toByte())
    } else if (argument < 0x10000u) {
        // head + 2 bytes
        val head = ((major.ordinal shl 5) or Minor.ARG_2.value.toInt()).toByte()
        return byteArrayOf(
            head,
            (argument shr 8 and 0xffu).toByte(),
            (argument and 0xffu).toByte(),
        )
    } else if (argument < 0x100000000u) {
        // head + 4 bytes
        val head = ((major.ordinal shl 5) or Minor.ARG_4.value.toInt()).toByte()
        return byteArrayOf(
            head,
            (argument shr 24 and 0xffu).toByte(),
            (argument shr 16 and 0xffu).toByte(),
            (argument shr 8 and 0xffu).toByte(),
            (argument and 0xffu).toByte(),
        )
    } else {
        // head + 8 bytes
        val head = ((major.ordinal shl 5) or Minor.ARG_8.value.toInt()).toByte()
        return byteArrayOf(
            head,
            (argument shr 56 and 0xffu).toByte(),
            (argument shr 48 and 0xffu).toByte(),
            (argument shr 40 and 0xffu).toByte(),
            (argument shr 32 and 0xffu).toByte(),
            (argument shr 24 and 0xffu).toByte(),
            (argument shr 16 and 0xffu).toByte(),
            (argument shr 8 and 0xffu).toByte(),
            (argument and 0xffu).toByte(),
        )
    }
}

internal fun deserializeArgument(buffer: SdkBufferedSource): ULong {
    val minorByte = buffer.readByte().toUByte() and MINOR_MASK

    if (minorByte < Minor.ARG_1.value) {
        return minorByte.toULong()
    }

    return when (Minor.fromValue(minorByte)) {
        Minor.ARG_1 -> buffer.readByte().toUByte().toULong()
        Minor.ARG_2 -> {
            val bytes = SdkBuffer().use {
                if (buffer.read(it, 2) != 2L) { throw DeserializationException("Unexpected end of payload") }
                it.readByteArray()
            }
            return bytes.toULong()
        }
        Minor.ARG_4 -> {
            val bytes = SdkBuffer().use {
                if (buffer.read(it, 4) != 4L) { throw DeserializationException("Unexpected end of payload") }
                it.readByteArray()
            }
            return bytes.toULong()
        }
        Minor.ARG_8 -> {
            val bytes = SdkBuffer().use {
                if (buffer.read(it, 8) != 8L) { throw DeserializationException("Unexpected end of payload") }
                it.readByteArray()
            }
            return bytes.toULong()
        }
        else -> throw DeserializationException("Unsupported minor value ${Minor.fromValue(minorByte).value.toULong()}, expected one of ${Minor.ARG_1}, ${Minor.ARG_2}, ${Minor.ARG_4}, ${Minor.ARG_8}.")
    }
}

// Convert a ByteArray to a ULong by extracting each byte and left-shifting it appropriately.
private fun ByteArray.toULong() = foldIndexed(0uL) { i, acc, byte ->
    acc or (byte.toUByte().toULong() shl ((size - 1 - i) * 8))
}

internal fun decodeNextValue(buffer: SdkBufferedSource): Cbor.Value {
    val major = peekMajor(buffer)
    val minor = peekMinorByte(buffer)

    return when (major) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer)
        Major.NEG_INT -> Cbor.Encoding.NegInt.decode(buffer)
        Major.BYTE_STRING -> Cbor.Encoding.ByteString.decode(buffer)
        Major.STRING -> Cbor.Encoding.String.decode(buffer)
        Major.LIST -> {
            return if (minor == Minor.INDEFINITE.value) {
                Cbor.Encoding.IndefiniteList.decode(buffer)
            } else {
                Cbor.Encoding.List.decode(buffer)
            }
        }
        Major.MAP -> {
            if (minor == Minor.INDEFINITE.value) {
                Cbor.Encoding.IndefiniteMap.decode(buffer)
            } else {
                Cbor.Encoding.Map.decode(buffer)
            }
        }
        Major.TAG -> Cbor.Encoding.Tag.decode(buffer)
        Major.TYPE_7 -> {
            when (minor) {
                Minor.TRUE.value -> Cbor.Encoding.Boolean.decode(buffer)
                Minor.FALSE.value -> Cbor.Encoding.Boolean.decode(buffer)
                Minor.NULL.value -> Cbor.Encoding.Null.decode(buffer)
                Minor.UNDEFINED.value -> Cbor.Encoding.Null.decode(buffer)
                Minor.FLOAT16.value -> Cbor.Encoding.Float16.decode(buffer)
                Minor.FLOAT32.value -> Cbor.Encoding.Float32.decode(buffer)
                Minor.FLOAT64.value -> Cbor.Encoding.Float64.decode(buffer)
                Minor.INDEFINITE.value -> Cbor.Encoding.IndefiniteBreak.decode(buffer)
                else -> throw DeserializationException("Unexpected type 7 minor value $minor")
            }
        }
    }
}

// Converts a [ByteArray] to a [String] representing a BigInteger.
private fun ByteArray.toBigInteger(): BigInteger {
    var decimal = "0"

    // Iterate through each byte in the array
    for (byte in this) {
        val binaryString = byte.toUByte().toString(2).padStart(8, '0') // Convert each byte to an 8-bit binary string

        // For each bit, update the decimal string
        for (bit in binaryString) {
            decimal = decimal.multiplyByTwo() // Multiply current decimal by 2 (shift left)
            if (bit == '1') {
                decimal = decimal.addOne() // Add 1 if the bit is 1
            }
        }
    }

    return BigInteger(decimal)
}

// Helper function to multiply a decimal string by 2
private fun String.multiplyByTwo(): String {
    var carry = 0
    val result = StringBuilder()

    // Start from the least significant digit (rightmost)
    for (i in this.lastIndex downTo 0) {
        val digit = this[i] - '0'
        val newDigit = digit * 2 + carry
        result.insert(0, newDigit % 10) // Insert at the beginning of the result
        carry = newDigit / 10
    }

    if (carry > 0) {
        result.insert(0, carry)
    }

    return result.toString()
}

// Helper function to add 1 to a decimal string
private fun String.addOne(): String {
    var carry = 1
    val result = StringBuilder(this)

    // Start from the least significant digit (rightmost)
    for (i in this.lastIndex downTo 0) {
        if (carry == 0) break

        val digit = result[i] - '0'
        val newDigit = digit + carry
        result[i] = (newDigit % 10 + '0'.code).toChar()
        carry = newDigit / 10
    }

    if (carry > 0) {
        result.insert(0, carry)
    }

    return result.toString()
}
