/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import kotlin.math.pow
import kotlin.test.assertEquals

private const val MAX_DEPTH = 64

/**
 * Assert CBOR documents for semantic equality: map key order, definite vs indefinite lengths, and float widths that
 * preserve the value are ignored, while major types and tags are compared.
 *
 * Malformed input fails with an [IllegalStateException]; a semantic mismatch fails with an [AssertionError].
 */
internal fun assertCborEqual(expected: ByteArray, actual: ByteArray, message: String) {
    val expectedValue = CborReader(expected).readDocument()
    val actualValue = CborReader(actual).readDocument()
    assertEquals(expectedValue, actualValue, message)
}

internal sealed interface CborValue {
    // the full 65-bit CBOR integer range doesn't fit a Long, so integers are kept as decimal strings
    data class Integer(val value: String) : CborValue
    data class Bytes(val value: List<Byte>) : CborValue
    data class Text(val value: String) : CborValue
    data class Array(val items: List<CborValue>) : CborValue
    data class Map(val entries: kotlin.collections.Map<CborValue, CborValue>) : CborValue
    data class Tagged(val tag: ULong, val value: CborValue) : CborValue
    data class Bool(val value: Boolean) : CborValue

    // compared by bit pattern: NaNs are all equal and -0.0 differs from 0.0, on every platform
    data class Float(val bits: Long) : CborValue
    data object Null : CborValue
    data object Undefined : CborValue
    data class Simple(val value: Int) : CborValue
}

private fun floatOf(value: Double) = CborValue.Float(if (value.isNaN()) Double.NaN.toRawBits() else value.toRawBits())

private object Break

private class CborReader(private val bytes: ByteArray) {
    private var pos = 0
    private var depth = 0

    fun readDocument(): CborValue {
        val value = readValue()
        check(value is CborValue) { "unexpected CBOR break at offset ${pos - 1}" }
        check(pos == bytes.size) { "trailing bytes after CBOR document at offset $pos" }
        return value
    }

    private fun readByte(): Int {
        check(pos < bytes.size) { "unexpected end of CBOR input at offset $pos" }
        return bytes[pos++].toInt() and 0xff
    }

    private fun readBytes(count: Int): ByteArray {
        check(count <= bytes.size - pos) { "unexpected end of CBOR input at offset $pos" }
        return bytes.copyOfRange(pos, pos + count).also { pos += count }
    }

    private fun readUInt(byteCount: Int): ULong {
        var result = 0uL
        repeat(byteCount) { result = (result shl 8) or readByte().toULong() }
        return result
    }

    // returns null for the indefinite-length marker
    private fun readArgument(minor: Int): ULong? = when (minor) {
        in 0..23 -> minor.toULong()
        24 -> readUInt(1)
        25 -> readUInt(2)
        26 -> readUInt(4)
        27 -> readUInt(8)
        31 -> null
        else -> error("reserved CBOR additional info $minor at offset ${pos - 1}")
    }

    private fun readDefiniteArgument(minor: Int, major: Int): ULong = readArgument(minor) ?: error("indefinite length is not allowed for major type $major at offset ${pos - 1}")

    // a length or count that exceeds the remaining input is malformed, and must not be truncated to an Int
    private fun checkedCount(count: ULong, minBytesPerItem: Int): Int {
        check(count <= ((bytes.size - pos) / minBytesPerItem).toULong()) { "CBOR length $count exceeds the remaining input at offset $pos" }
        return count.toInt()
    }

    private fun readValue(): Any {
        check(depth < MAX_DEPTH) { "CBOR nesting exceeds $MAX_DEPTH at offset $pos" }
        depth++
        try {
            val initial = readByte()
            val major = initial shr 5
            val minor = initial and 0x1f

            return when (major) {
                0 -> CborValue.Integer(readDefiniteArgument(minor, major).toString())
                1 -> CborValue.Integer(negativeToString(readDefiniteArgument(minor, major)))
                2 -> CborValue.Bytes(readString(major, minor).toList())
                3 -> CborValue.Text(decodeUtf8(readString(major, minor)))
                4 -> CborValue.Array(readItems(minor, itemsPerEntry = 1))
                5 -> {
                    val items = readItems(minor, itemsPerEntry = 2)
                    val entries = items.chunked(2).associate { (key, value) -> key to value }
                    check(entries.size == items.size / 2) { "duplicate CBOR map key before offset $pos" }
                    CborValue.Map(entries)
                }
                6 -> CborValue.Tagged(readDefiniteArgument(minor, major), readItem())
                7 -> readSimpleOrFloat(minor)
                else -> error("unreachable CBOR major type $major")
            }
        } finally {
            depth--
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (e: Exception) {
        error("invalid UTF-8 in CBOR text string before offset $pos")
    }

    private fun readItem(): CborValue {
        val value = readValue()
        check(value is CborValue) { "unexpected CBOR break at offset ${pos - 1}" }
        return value
    }

    // the value is -1 - argument; argument + 1 overflows only for ULong.MAX_VALUE, i.e. -2^64
    private fun negativeToString(argument: ULong): String {
        if (argument == ULong.MAX_VALUE) return "-18446744073709551616"
        return "-${argument + 1uL}"
    }

    private fun readString(major: Int, minor: Int): ByteArray {
        val length = readArgument(minor) ?: return buildList {
            while (true) {
                val chunkInitial = readByte()
                if (chunkInitial == 0xff) break
                check(chunkInitial shr 5 == major) { "indefinite-length string chunk has the wrong major type at offset ${pos - 1}" }
                check(chunkInitial and 0x1f != 31) { "indefinite-length string chunks must be definite at offset ${pos - 1}" }
                addAll(readString(major, chunkInitial and 0x1f).toList())
            }
        }.toByteArray()
        return readBytes(checkedCount(length, 1))
    }

    private fun readItems(minor: Int, itemsPerEntry: Int): List<CborValue> {
        val count = readArgument(minor)
        if (count != null) {
            val total = checkedCount(count, minBytesPerItem = itemsPerEntry) * itemsPerEntry
            return buildList(total) { repeat(total) { add(readItem()) } }
        }
        return buildList {
            while (true) {
                val item = readValue()
                if (item === Break) break
                add(item as CborValue)
            }
            check(size % itemsPerEntry == 0) { "indefinite-length map has a key without a value before offset $pos" }
        }
    }

    private fun readSimpleOrFloat(minor: Int): Any = when (minor) {
        in 0..19 -> CborValue.Simple(minor)
        20 -> CborValue.Bool(false)
        21 -> CborValue.Bool(true)
        22 -> CborValue.Null
        23 -> CborValue.Undefined
        24 -> readByte().also { check(it >= 32) { "simple value $it must use the one byte form at offset ${pos - 1}" } }.let(CborValue::Simple)
        25 -> floatOf(halfToDouble(readUInt(2).toInt()))
        26 -> floatOf(Float.fromBits(readUInt(4).toInt()).toDouble())
        27 -> floatOf(Double.fromBits(readUInt(8).toLong()))
        31 -> Break
        else -> error("reserved CBOR additional info $minor at offset ${pos - 1}")
    }

    private fun halfToDouble(bits: Int): Double {
        val sign = if (bits and 0x8000 != 0) -1.0 else 1.0
        val exponent = (bits shr 10) and 0x1f
        val fraction = bits and 0x3ff
        return when (exponent) {
            0 -> sign * fraction * 2.0.pow(-24)
            31 -> if (fraction == 0) sign * Double.POSITIVE_INFINITY else Double.NaN
            else -> sign * (1 + fraction / 1024.0) * 2.0.pow(exponent - 15)
        }
    }
}
