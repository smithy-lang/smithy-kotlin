/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.cbor.encoding.*
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Major
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Minor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.majorOf
import aws.smithy.kotlin.runtime.serde.cbor.encoding.minorOf
import aws.smithy.kotlin.runtime.serde.cbor.encoding.peekHead

/**
 * Encode and write a CBOR [Value] to this [SdkBuffer]
 */
internal fun SdkBuffer.write(value: Value) = value.encode(this)

// Peek at the head byte to determine if the next encoded value represents a break in an indefinite-length list/map
internal val SdkBufferedSource.nextValueIsIndefiniteBreak: kotlin.Boolean
    get() {
        val head = peekHead(this)
        return majorOf(head) == Major.TYPE_7 && minorOf(head) == Minor.INDEFINITE.value
    }

// Peek at the head byte to determine if the next encoded value represents null
internal val SdkBufferedSource.nextValueIsNull: kotlin.Boolean
    get() {
        val head = peekHead(this)
        return majorOf(head) == Major.TYPE_7 && (minorOf(head) == Minor.NULL.value || minorOf(head) == Minor.UNDEFINED.value)
    }

// Encodes a [Major] and [Minor] value in a single byte
internal fun encodeMajorMinor(major: Major, minor: Minor): Byte = (major.value.toUInt() shl 5 or minor.value.toUInt()).toByte()

// Encode a [Major] value along with its additional information / argument, writing directly to [into].
// CBOR arguments are big-endian; using fixed-width writes avoids allocating an intermediate ByteArray
// (and boxing each byte) on every integer / length / tag id that is serialized.
internal fun SdkBufferedSink.writeArgument(major: Major, argument: ULong) {
    val majorBits = major.ordinal shl 5
    when {
        argument < 24u -> writeByte((majorBits.toULong() or argument).toByte())
        argument < 0x100u -> {
            writeByte((majorBits or Minor.ARG_1.value.toInt()).toByte())
            writeByte(argument.toByte())
        }
        argument < 0x10000u -> {
            writeByte((majorBits or Minor.ARG_2.value.toInt()).toByte())
            writeShort(argument.toShort())
        }
        argument < 0x100000000u -> {
            writeByte((majorBits or Minor.ARG_4.value.toInt()).toByte())
            writeInt(argument.toInt())
        }
        else -> {
            writeByte((majorBits or Minor.ARG_8.value.toInt()).toByte())
            writeLong(argument.toLong())
        }
    }
}

// Convert a ByteArray to a ULong by left-shifting each byte appropriately
internal fun ByteArray.toULong() = foldIndexed(0uL) { i, acc, byte ->
    acc or (byte.toUByte().toULong() shl ((size - 1 - i) * 8))
}
