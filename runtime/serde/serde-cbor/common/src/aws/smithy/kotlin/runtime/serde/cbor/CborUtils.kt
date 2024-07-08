/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.io.use
import aws.smithy.kotlin.runtime.serde.DeserializationException

/**
 * Encode and write a [Cbor.Value] to this [SdkBuffer]
 */
internal fun SdkBuffer.write(value: Cbor.Value) = write(value.encode())

// Peek at the head byte to determine if the next encoded value represents a break in an indefinite-length list/map
internal val SdkBufferedSource.nextValueIsIndefiniteBreak: Boolean
    get() = peekMajor(this) == Major.TYPE_7 && peekMinorByte(this) == Minor.INDEFINITE.value

// Peek at the head byte to determine if the next encoded value represents null
internal val SdkBufferedSource.nextValueIsNull: Boolean
    get() = peekMajor(this) == Major.TYPE_7 && (peekMinorByte(this) == Minor.NULL.value || peekMinorByte(this) == Minor.UNDEFINED.value)

// Encodes a major and minor type of CBOR value in a single byte
internal fun encodeMajorMinor(major: Major, minor: Minor): Byte = (major.value.toUInt() shl 5 or minor.value.toUInt()).toByte()

// Encode a [Major] value along with its additional information / argument.
internal fun encodeArgument(major: Major, argument: ULong): ByteArray {
    // Convert a ULong to a ByteArray by right-shifting it appropriately
    fun ULong.toByteArray(): ByteArray {
        val argumentByteLength = when {
            this < 24u -> 0
            this < 0x100u -> 1
            this < 0x10000u -> 2
            this < 0x100000000u -> 4
            else -> 8
        }

        val argumentBytes = ((argumentByteLength - 1) downTo 0).map { index ->
            (this shr (index * 8) and 0xffu).toByte()
        }.toByteArray()
        return argumentBytes
    }

    val head = when {
        argument < 24u -> ((major.ordinal shl 5).toULong() or argument).toByte()
        argument < 0x100u -> ((major.ordinal shl 5) or Minor.ARG_1.value.toInt()).toByte()
        argument < 0x10000u -> ((major.ordinal shl 5) or Minor.ARG_2.value.toInt()).toByte()
        argument < 0x100000000u -> ((major.ordinal shl 5) or Minor.ARG_4.value.toInt()).toByte()
        else -> ((major.ordinal shl 5) or Minor.ARG_8.value.toInt()).toByte()
    }

    return byteArrayOf(head, *argument.toByteArray())
}

// Convert a ByteArray to ULong by left-shifting each byte appropriately
internal fun ByteArray.toULong() = foldIndexed(0uL) { i, acc, byte ->
    acc or (byte.toUByte().toULong() shl ((size - 1 - i) * 8))
}

// Converts a [BigInteger] to a [ByteArray].
internal fun BigInteger.asBytes(): ByteArray {
    var decimal = this.toString().removePrefix("-")
    val binary = StringBuilder()

    // Convert decimal to binary
    while (decimal != "0") {
        val temp = StringBuilder()
        var carry = 0
        for (c in decimal) {
            val num = carry * 10 + c.digitToInt()
            temp.append(num / 2)
            carry = num % 2
        }
        binary.insert(0, carry)

        decimal = temp
            .dropWhile { it == '0' }
            .ifEmpty { "0" }
            .toString()
    }

    // Ensure the binary string length is a multiple of 8
    val zeroPrefixLength = (8 - binary.length % 8) % 8
    val paddedBinary = "0".repeat(zeroPrefixLength) + binary

    // Convert each set of 8 bits to a byte
    return paddedBinary.chunked(8)
        .map { it.toUByte(radix = 2).toByte() }
        .toByteArray()
}

// Converts a [ByteArray] to a [String] representing a BigInteger.
internal fun ByteArray.toBigInteger(): BigInteger {
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
