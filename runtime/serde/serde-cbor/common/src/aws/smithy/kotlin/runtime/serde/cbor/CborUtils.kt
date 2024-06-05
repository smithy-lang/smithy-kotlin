package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import kotlin.experimental.and

/**
 * Represents CBOR major types (0 for unsigned integer, 1 for negative integer, etc.)
 */
internal enum class Major(val value: UByte) {
    U_INT(0u),
    NEG_INT(1u),
    BYTE_STRING(2u),
    STRING(3u),
    LIST(4u),
    MAP(5u),
    TAG(6u),
    TYPE_7(7u);

    companion object {
        fun fromValue(value: UByte): Major = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("$value is not a valid Major value.")
    }
}

/**
 * Represents CBOR minor types (aka "additional information")
 */
internal enum class Minor(val value: UByte) {
    ARG_1(24u),
    ARG_2(25u),
    ARG_4(26u),
    ARG_8(27u),
    INDEFINITE(31u),

    // The following minor values are only to be used with major type 7
    FALSE(20u),
    TRUE(21u),
    NULL(22u),
    UNDEFINED(23u), // undefined should be deserialized as `null`
    FLOAT16(25u),
    FLOAT32(26u),
    FLOAT64(27u);

    companion object {
        fun fromValue(value: UByte): Minor = Minor.entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("$value is not a valid Minor value.")
    }
}

internal val MAJOR_MASK: UByte = 0b111u
internal val MINOR_MASK: UByte = 0b11111u

internal fun peekTag(buffer: SdkBufferedSource) = Cbor.Encoding.Tag.decode(buffer.peek())

internal fun peekMajor(buffer: SdkBufferedSource): Major {
    val majorByte = buffer.peek().readByte().toUByte()
    val masked = ((majorByte.toUInt() shr 5).toUByte()) and MAJOR_MASK
    return Major.fromValue(masked)
}

internal fun peekMinor(buffer: SdkBufferedSource): Minor {
    val minorByte = buffer.peek().readByte().toUByte()
    val masked = minorByte and MINOR_MASK
    // 11110111
    // AND
    // 00011111
    // =
    // 0001 0111 -> 0x17 -> 23

    return Minor.fromValue(masked)
}

internal fun peekMinorSafe(buffer: SdkBufferedSource): Minor? {
    val minorByte = buffer.peek().readByte().toUByte()
    val masked = minorByte and MINOR_MASK
    // 11110111
    // AND
    // 00011111
    // =
    // 0001 0111 -> 0x17 -> 23

    return try {
        Minor.fromValue(masked)
    } catch (e: Exception) {
        null
    }
}

internal fun peekMinorRaw(buffer: SdkBufferedSource): UByte {
    val minorByte = buffer.peek().readByte().toUByte()
    return minorByte and MINOR_MASK
}


// Subtracts one from the given BigInteger
internal fun BigInteger.minusOne(): BigInteger {
    val digits = toString().toCharArray()

    var index = digits.lastIndex
    while (index >= 0) {
        if (digits[index] == '0') {
            digits[index] = '9'
            index--
        } else {
            digits[index] = digits[index] - 1
            break
        }
    }

    val result = digits.concatToString()

    return if (result.startsWith("0") && result.length > 1) {
        BigInteger(result.substring(1))
    } else {
        BigInteger(result)
    }
}

// Adds one to the given BigInteger
internal fun BigInteger.plusOne(): BigInteger {
    val digits = toString().toCharArray()

    var index = digits.lastIndex
    while (index >= 0) {
        if (digits[index] == '9') {
            digits[index] = '0'
            index--
        } else {
            digits[index] = digits[index] + 1
            break
        }
    }

    return if (index == -1) {
        BigInteger("1${digits.concatToString()}")
    } else {
        BigInteger(digits.concatToString())
    }
}


// Converts a [BigInteger] to a [ByteArray].
internal fun BigInteger.toByteArray(): ByteArray {
    var decimal = this.toString()
    val binary = StringBuilder()
    while (decimal != "0") {
        val temp = StringBuilder()
        var carry = 0
        for (c in decimal) {
            val num = carry * 10 + c.code
            temp.append(num / 2)
            carry = num % 2
        }
        binary.insert(0, carry)

        decimal = if (temp[0] == '0' && temp.length > 1) {
            temp.substring(1)
        } else {
            temp.toString()
        }

        if (decimal.all { it == '0' }) { decimal = "0" }
    }

    return binary
        .padStart(8 - binary.length % 8, '0') // ensure binary string is zero-padded
        .chunked(8) { it.toString().toByte() } // convert each set of 8 bits to a byte
        .toByteArray()
}