/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSource

/**
 * Represents CBOR major types (0 for unsigned integer, 1 for negative integer, etc...)
 */
internal enum class Major(val value: UByte) {
    U_INT(0u),
    NEG_INT(1u),
    BYTE_STRING(2u),
    STRING(3u),
    LIST(4u),
    MAP(5u),
    TAG(6u),
    TYPE_7(7u),
    ;

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
    UNDEFINED(23u), // note: undefined should be deserialized to `null`
    FLOAT16(25u),
    FLOAT32(26u),
    FLOAT64(27u),
    ;
}

internal val MAJOR_BYTE_MASK: UByte = 0b111u
internal val MINOR_BYTE_MASK: UByte = 0b11111u

internal fun peekMajor(buffer: SdkBufferedSource): Major {
    val byte = buffer.peek().readByte().toUByte()
    val major = ((byte.toUInt() shr 5).toUByte()) and MAJOR_BYTE_MASK
    return Major.fromValue(major)
}

internal fun peekMinorByte(buffer: SdkBufferedSource): UByte {
    val byte = buffer.peek().readByte().toUByte()
    return byte and MINOR_BYTE_MASK
}

internal fun peekTag(buffer: SdkBufferedSource) = Cbor.Encoding.Tag.decode(buffer.peek())

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
internal fun BigInteger.asBytes(): ByteArray {
    var decimal = this.toString().removePrefix("-")
    val binary = StringBuilder()

    while (decimal != "0") {
        val temp = StringBuilder()
        var carry = 0
        for (c in decimal) {
            val num = carry * 10 + c.digitToInt()
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

    val zeroPrefixLength = 8 - binary.length % 8 // prepend with zeroes if the total string length is not divisible by 8
    return ("0".repeat(zeroPrefixLength) + binary)
        .chunked(8) { it.toString().toUByte(radix = 2).toByte() } // convert each set of 8 bits to a byte
        .toByteArray()
}

/**
 * Encode and write a [Cbor.Value] to this [SdkBuffer]
 */
internal fun SdkBuffer.write(value: Cbor.Value) = write(value.encode())
