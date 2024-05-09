package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBuffer
import kotlin.experimental.and

/**
 * Represents CBOR major types (0 for unsigned integer, 1 for negative integer, etc.)
 */
internal enum class Major(val value: Byte) {
    U_INT(0),
    NEG_INT(1),
    BYTE_STRING(2),
    STRING(3),
    LIST(4),
    MAP(5),
    TAG(6),
    TYPE_7(7);

    companion object {
        fun fromValue(value: Byte): Major = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("$value is not a valid Major value.")
    }
}

/**
 * Represents CBOR minor types (aka "additional information")
 */
internal enum class Minor(val value: Byte) {
    ARG_1(24),
    ARG_2(25),
    ARG_4(26),
    ARG_8(27),
    INDEFINITE(31),

    // The following minor values are only to be used with major type 7
    FALSE(20),
    TRUE(21),
    NULL(22),
    FLOAT16(25),
    FLOAT32(26),
    FLOAT64(27);

    companion object {
        fun fromValue(value: Byte): Minor = Minor.entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("$value is not a valid Minor value.")
    }
}

internal const val MAJOR_MASK = (0b111 shl 5).toByte()
internal const val MINOR_MASK = 0b11111.toByte()

internal fun peekTag(buffer: SdkBuffer) = Cbor.Encoding.Tag.decode(buffer.peek().buffer)

internal fun peekMajor(buffer: SdkBuffer) = Major.fromValue(buffer.readByte() and MAJOR_MASK)

internal fun peekMinor(buffer: SdkBuffer) = Minor.fromValue(buffer.readByte() and MINOR_MASK)


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
