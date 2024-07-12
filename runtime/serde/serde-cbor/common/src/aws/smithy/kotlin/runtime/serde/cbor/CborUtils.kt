/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.cbor.encoding.*
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Major
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Minor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.peekMajor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.peekMinorByte

/**
 * Encode and write a CBOR [Value] to this [SdkBuffer]
 */
internal fun SdkBuffer.write(value: Value) = value.encode(this)

// Peek at the head byte to determine if the next encoded value represents a break in an indefinite-length list/map
internal val SdkBufferedSource.nextValueIsIndefiniteBreak: kotlin.Boolean
    get() = peekMajor(this) == Major.TYPE_7 && peekMinorByte(this) == Minor.INDEFINITE.value

// Peek at the head byte to determine if the next encoded value represents null
internal val SdkBufferedSource.nextValueIsNull: kotlin.Boolean
    get() = peekMajor(this) == Major.TYPE_7 && (peekMinorByte(this) == Minor.NULL.value || peekMinorByte(this) == Minor.UNDEFINED.value)

// Encodes a [Major] and [Minor] value in a single byte
internal fun encodeMajorMinor(major: Major, minor: Minor): Byte = (major.value.toUInt() shl 5 or minor.value.toUInt()).toByte()

// Encode a [Major] value along with its additional information / argument
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

// Convert a ByteArray to a ULong by left-shifting each byte appropriately
internal fun ByteArray.toULong() = foldIndexed(0uL) { i, acc, byte ->
    acc or (byte.toUByte().toULong() shl ((size - 1 - i) * 8))
}
