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

internal object Cbor {
    /**
     * Represents an encodable / decodable CBOR value.
     */
    internal interface Value {
        /**
         * The [Major] value of the CBOR [Value]
         */
        val major: Major

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
            override val major = Major.U_INT

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
         * Note: This class takes an *unsigned* long as input, the negative is implied.
         * Values will be properly encoded / decoded according to the CBOR specification (-1 minus $value)
         */
        internal class NegInt(val value: ULong) : Value {
            override val major = Major.NEG_INT

            override fun encode(): ByteArray = encodeArgument(Major.NEG_INT, (value - 1u))

            internal companion object {
                fun decode(buffer: SdkBufferedSource): NegInt {
                    val argument: ULong = deserializeArgument(buffer)
                    return NegInt(argument + 1u)
                }
            }
        }

        /**
         * Represents a CBOR byte string (major type 2).
         * @param value The [ByteArray] which this CBOR byte string represents.
         */
        internal class ByteString(val value: ByteArray) : Value {
            override val major = Major.BYTE_STRING

            override fun encode(): ByteArray {
                val head = encodeArgument(Major.BYTE_STRING, value.size.toULong())
                return byteArrayOf(*head, *value)
            }

            internal companion object {
                fun decode(buffer: SdkBufferedSource): ByteString {
                    val minor = peekMinorSafe(buffer)

                    return if (minor == Minor.INDEFINITE) {
                        val list = IndefiniteList.decode(buffer).value

                        val buffer = SdkBuffer()
                        list.forEach {
                            buffer.write((it as ByteString).value)
                        }

                        ByteString(buffer.readByteArray())
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
            override val major = Major.STRING

            override fun encode(): ByteArray {
                val head = encodeArgument(Major.STRING, value.length.toULong())
                return byteArrayOf(*head, *value.encodeToByteArray())
            }

            internal companion object {
                fun decode(buffer: SdkBufferedSource): String {
                    val minor = peekMinorSafe(buffer)

                    return if (minor == Minor.INDEFINITE) {
                        val list = IndefiniteList.decode(buffer).value

                        val sb = StringBuilder()
                        list.forEach {
                            sb.append((it as String).value)
                        }

                        String(sb.toString())

                    } else {
                        val length = deserializeArgument(buffer).toInt()
                        val bytes = ByteArray(length)

                        if (length > 0) {
                            val rc = buffer.read(bytes)
                            check(rc == length) { "Unexpected end of CBOR string: expected $length bytes, got $rc." }
                        }

                        return String(bytes.decodeToString())
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
            override val major = Major.LIST

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
                        values[i] = decodeNextValue(buffer)
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
            override val major = Major.TYPE_7

            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.LIST, Minor.INDEFINITE))

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): IndefiniteList {
                    buffer.readByte() // discard head

                    val list = mutableListOf<Value>()
                    while (decodeNextValue(buffer.peek()) !is IndefiniteBreak) {
                        list.add(decodeNextValue(buffer))
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
            override val major = Major.MAP

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
                internal fun decode(buffer: SdkBufferedSource) : Map {
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
            override val major = Major.TYPE_7

            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.MAP, Minor.INDEFINITE))

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): IndefiniteMap {
                    buffer.readByte() // discard head byte
                    val valueMap = mutableMapOf<String, Value>()

                    while (peekMajor(buffer) != Major.TYPE_7 && peekMinor(buffer) != Minor.INDEFINITE) {
                        val key = String.decode(buffer)
                        val value = decodeNextValue(buffer)
                        valueMap[key] = value
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
            override val major = Major.TAG

            override fun encode(): ByteArray = byteArrayOf(*encodeArgument(Major.TAG, id), *value.encode())

            internal companion object {
                fun decode(buffer: SdkBufferedSource): Tag {
                    return when (val id = peekMinor(buffer).value.toInt()) {
                        1 -> { Tag(id.toULong(), Timestamp.decode(buffer)) } // TODO Timestamp
                        2 -> { Tag(id.toULong(), BigNum.decode(buffer)) } // TODO unsigned big integer
                        3 -> { Tag(id.toULong(), NegBigNum.decode(buffer)) } // TODO negative big integer
                        4 -> { Tag(id.toULong(), DecimalFraction.decode(buffer)) } // TODO BigDecimal (decimal fraction)
                        else -> throw DeserializationException("Unknown tag ID $id")
                    }
                }
            }
        }

        /**
         * Represents a CBOR boolean (major type 7). The minor type is 5 for false and 6 for true.
         * @param value the [kotlin.Boolean] this CBOR boolean represents.
         */
        internal class Boolean(val value: kotlin.Boolean): Value {
            override val major = Major.TYPE_7

            override fun encode(): ByteArray = byteArrayOf(when (value) {
                false -> encodeMajorMinor(Major.TYPE_7, Minor.FALSE)
                true -> encodeMajorMinor(Major.TYPE_7, Minor.TRUE)
            })

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): Boolean {
                    val major = peekMajor(buffer)
                    check (major == Major.TYPE_7) { "Expected ${Major.TYPE_7} for CBOR boolean, got $major" }

                    return when (val minor = peekMinor(buffer)) {
                        Minor.FALSE -> { Boolean(false) }
                        Minor.TRUE -> { Boolean(true) }
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
            override val major = Major.TYPE_7

            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.TYPE_7, Minor.NULL))

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): Null {
                    val major = peekMajor(buffer)
                    check (major == Major.TYPE_7) { "Expected ${Major.TYPE_7} for CBOR null, got $major" }

                    val minor = peekMinor(buffer)
                    check (minor == Minor.NULL || minor == Minor.UNDEFINED) { "Expected ${Minor.NULL} or ${Minor.UNDEFINED} for CBOR null, got $minor" }

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
            override val major = Major.TYPE_7

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
            override val major = Major.TYPE_7

            override fun encode(): ByteArray {
                val bits = value.toRawBits()
                return byteArrayOf(
                    encodeMajorMinor(Major.TYPE_7, Minor.FLOAT32),
                    (bits shr 24 and 0xff).toByte(),
                    (bits shr 16 and 0xff).toByte(),
                    (bits shr 8 and 0xff).toByte(),
                    (bits and 0xff).toByte()
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
            override val major = Major.TYPE_7

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
                    (bits and 0xff).toByte()
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
            override val major: Major = Major.TAG

            override fun encode(): ByteArray = byteArrayOf(*Tag(1u, Float64(value.epochMilliseconds / 1000.toDouble())).encode())

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource) : Timestamp {
                    val tagId = deserializeArgument(buffer).toInt()
                    check(tagId == 1) { "Expected tag ID 1 for CBOR timestamp, got $tagId" }

                    val major = peekMajor(buffer)
                    val minor = peekMinor(buffer)

                    val instant: Instant = when (major) {
                        Major.U_INT -> {
                            val timestamp = UInt.decode(buffer).value.toLong() // note: possible truncation here because kotlin.time.Instant takes a Long, not a ULong
                            Instant.fromEpochSeconds(timestamp)
                        }
                        Major.NEG_INT -> {
                            val negativeTimestamp: Long = 0L - NegInt.decode(buffer).value.toLong() // note: possible truncation here because kotlin.time.Instant takes a Long, not a ULong
                            Instant.fromEpochSeconds(negativeTimestamp)
                        }
                        Major.TYPE_7 -> {
                            val doubleTimestamp: Double = when (minor) {
                                Minor.FLOAT16 -> { Float16.decode(buffer).value.toDouble() }
                                Minor.FLOAT32 -> { Float32.decode(buffer).value.toDouble() }
                                Minor.FLOAT64 -> { Float64.decode(buffer).value }
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
            override val major = Major.TAG

            override fun encode(): ByteArray = byteArrayOf(*Tag(2u, ByteString(value.toByteArray())).encode())

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
            override val major = Major.TAG

            // TODO Ensure negative sign (-) is handled correctly.
            override fun encode(): ByteArray = byteArrayOf(*Tag(3u, ByteString(value.minusOne().toByteArray())).encode())

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
            override val major = Major.TAG

            override fun encode(): ByteArray {
                val str = value.toPlainString()

                val dot = str
                    .indexOf('.')
                    .let { if (it == -1) str.length else it }

                val mantissa = str.substring(0, dot).toULong()
                val exp = if (dot == str.length) 0u else {
                    str.substring(dot+1).toULong()
                }

                // FIXME exponent and mantissa could be negative too...
                return byteArrayOf(*Tag(
                    4u,
                    List(listOf(UInt(exp), UInt(mantissa)))
                ).encode())
            }

            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): DecimalFraction {
                    val tagId = deserializeArgument(buffer).toInt()
                    check(tagId == 4) { "Expected tag ID 4 for CBOR decimal fraction, got $tagId" }

                    val array = List.decode(buffer)
                    check(array.value.size == 2) { "Expected array of length 2 for decimal fraction, got ${array.value.size}" }

                    val exponent = array.value[0]
                    val mantissa = array.value[1]

                    val sb = StringBuilder()

                    // append mantissa, prepend with '-' if negative.
                    when(mantissa.major) {
                        Major.U_INT -> { sb.append((mantissa as UInt).value.toString()) }
                        Major.NEG_INT -> {
                            sb.append("-")
                            sb.append((mantissa as UInt).value.toString())
                        }
                        else -> throw DeserializationException("Expected integer for CBOR decimal fraction mantissa value, got ${mantissa.major}.")
                    }

                    when (exponent.major) {
                        Major.U_INT -> {
                            // Suffix with zeroes
                            sb.append("0".repeat((exponent as UInt).value.toInt()))
                            sb.append(".")
                        }
                        Major.NEG_INT -> {
                            // Prefix with zeroes if necessary
                            val exponentValue = (exponent as NegInt).value.toInt()
                            if (exponentValue > sb.length) {
                                val insertIndex = if (sb[0] == '-') { 1 } else { 0 }
                                sb.insert(insertIndex, "0".repeat(sb.length - exponentValue))
                                sb.insert(insertIndex, '.')
                            } else {
                                sb.insert(sb.lastIndex - exponentValue, '.')
                            }
                        }
                        else -> throw DeserializationException("Expected integer for CBOR decimal fraction exponent value, got ${exponent.major}.")
                    }

                    return DecimalFraction(BigDecimal(sb.toString()))
                }
            }
        }

        /**
         * Represents the "break" stop-code for lists/maps with an indefinite length (major type 7, minor type 31).
         */
        internal class IndefiniteBreak : Value {
            override val major = Major.TYPE_7

            override fun encode(): ByteArray = byteArrayOf(encodeMajorMinor(Major.TYPE_7, Minor.INDEFINITE))
            internal companion object {
                internal fun decode(buffer: SdkBufferedSource): IndefiniteBreak {
                    val major = peekMajor(buffer)
                    check(major == Major.TYPE_7) { "Expected CBOR indefinite break stop-code to be major ${Major.TYPE_7}, got $major."}

                    val minor = peekMinor(buffer)
                    check(minor == Minor.INDEFINITE) { "Expected CBOR indefinite break stop-code to be minor ${Minor.INDEFINITE}, got $minor."}

                    buffer.readByte() // discard major/minor
                    return IndefiniteBreak()
                }
            }
        }
    }
}

/**
 * Returns the length of the CBOR value in bytes.
 * This includes the head (1 byte) and any additional bytes (1, 2, 4, or 8).
 */
internal fun getValueLength(value: ULong): Int = when {
    value < 24u -> 1
    value < 0x100u -> 2
    value < 0x10000u -> 3
    value < 0x100000000u -> 5
    else -> 9
}

// Encodes a major and minor type of CBOR value in a single byte
internal fun encodeMajorMinor(major: Major, minor: Minor): Byte = (major.ordinal shl 5 or minor.ordinal).toByte()

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
        return byteArrayOf(head,
            (argument shr 8 and 0xffu).toByte(),
            (argument and 0xffu).toByte()
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
    val minor = peekMinorSafe(buffer)

    return when (major) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer)
        Major.NEG_INT -> Cbor.Encoding.NegInt.decode(buffer)
        Major.BYTE_STRING -> Cbor.Encoding.ByteString.decode(buffer)
        Major.STRING -> Cbor.Encoding.String.decode(buffer)
        Major.LIST -> {
            return if (minor == Minor.INDEFINITE) {
//                buffer.readByte() // discard head
//                decodeNextValue(buffer)
                Cbor.Encoding.IndefiniteList.decode(buffer)
            } else {
                Cbor.Encoding.List.decode(buffer)
            }
        }
        Major.MAP -> {
            if (minor == Minor.INDEFINITE) {
                buffer.readByte() // discard head
                decodeNextValue(buffer)
//                Cbor.Encoding.IndefiniteMap.decode(buffer)
            } else {
                Cbor.Encoding.Map.decode(buffer)
            }
        }
        Major.TAG -> Cbor.Encoding.Tag.decode(buffer)
        Major.TYPE_7 -> {
            val minor = peekMinorRaw(buffer)
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
            decimal = decimal.multiplyByTwo()  // Multiply current decimal by 2 (shift left)
            if (bit == '1') {
                decimal = decimal.addOne()  // Add 1 if the bit is 1
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
        result.insert(0, newDigit % 10)  // Insert at the beginning of the result
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
