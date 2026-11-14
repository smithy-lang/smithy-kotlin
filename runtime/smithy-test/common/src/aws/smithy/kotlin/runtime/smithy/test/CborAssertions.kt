/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import kotlin.math.pow
import kotlin.test.assertEquals

/**
 * Assert CBOR documents for semantic equality: map key order, definite vs indefinite lengths, and float widths that
 * preserve the value are ignored, while major types and tags are compared.
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
    data class Float(val value: Double) : CborValue
    data object Null : CborValue
    data object Undefined : CborValue
    data class Simple(val value: Int) : CborValue
}

private object Break

private class CborReader(private val bytes: ByteArray) {
    private var pos = 0

    fun readDocument(): CborValue {
        val value = readValue()
        check(value is CborValue) { "unexpected CBOR break at offset ${pos - 1}" }
        check(pos == bytes.size) { "trailing bytes after CBOR document at offset $pos" }
        return value
    }

    private fun readByte(): Int {
        check(pos < bytes.size) { "unexpected end of CBOR input" }
        return bytes[pos++].toInt() and 0xff
    }

    private fun readBytes(count: Int): ByteArray {
        check(pos + count <= bytes.size) { "unexpected end of CBOR input" }
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
        else -> error("invalid CBOR additional info $minor")
    }

    private fun readValue(): Any {
        val initial = readByte()
        val major = initial shr 5
        val minor = initial and 0x1f

        return when (major) {
            0 -> CborValue.Integer(checkNotNull(readArgument(minor)).toString())
            1 -> CborValue.Integer(negativeToString(checkNotNull(readArgument(minor))))
            2 -> CborValue.Bytes(readString(major, minor).toList())
            3 -> CborValue.Text(readString(major, minor).decodeToString())
            4 -> CborValue.Array(readItems(minor))
            5 -> {
                val items = readItems(minor, pairs = true)
                CborValue.Map(items.chunked(2).associate { (key, value) -> key to value })
            }
            6 -> CborValue.Tagged(checkNotNull(readArgument(minor)), readValue() as CborValue)
            7 -> readSimpleOrFloat(minor)
            else -> error("unreachable CBOR major type $major")
        }
    }

    // the value is -1 - argument; argument + 1 overflows only for ULong.MAX_VALUE, i.e. -2^64
    private fun negativeToString(argument: ULong): String = if (argument == ULong.MAX_VALUE) "-18446744073709551616" else "-${argument + 1uL}"

    private fun readString(major: Int, minor: Int): ByteArray {
        val length = readArgument(minor) ?: return buildList {
            while (true) {
                val chunkInitial = readByte()
                if (chunkInitial == 0xff) break
                check(chunkInitial shr 5 == major) { "indefinite-length string chunk has wrong major type" }
                addAll(readString(major, chunkInitial and 0x1f).toList())
            }
        }.toByteArray()
        return readBytes(length.toInt())
    }

    private fun readItems(minor: Int, pairs: Boolean = false): List<CborValue> {
        val count = readArgument(minor)
        if (count != null) {
            val total = if (pairs) count.toInt() * 2 else count.toInt()
            return List(total) { readValue() as CborValue }
        }
        return buildList {
            while (true) {
                val item = readValue()
                if (item === Break) break
                add(item as CborValue)
            }
            check(!pairs || size % 2 == 0) { "indefinite-length map has a key without a value" }
        }
    }

    private fun readSimpleOrFloat(minor: Int): Any = when (minor) {
        20 -> CborValue.Bool(false)
        21 -> CborValue.Bool(true)
        22 -> CborValue.Null
        23 -> CborValue.Undefined
        24 -> CborValue.Simple(readByte())
        25 -> CborValue.Float(halfToDouble(readUInt(2).toInt()))
        26 -> CborValue.Float(Float.fromBits(readUInt(4).toInt()).toDouble())
        27 -> CborValue.Float(Double.fromBits(readUInt(8).toLong()))
        31 -> Break
        else -> CborValue.Simple(minor)
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
