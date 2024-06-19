/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.deserializeList
import aws.smithy.kotlin.runtime.serde.deserializeMap
import kotlin.test.*

/**
 * Convert a string representation of a hexadecimal encoded byte sequence to a [ByteArray]
 */
internal fun String.toByteArray(): ByteArray = this
    .removePrefix("0x")
    .replace(Regex("\\s"), "")
    .padStart(length % 2, '0')
    .chunked(2)
    .map { hex -> hex.toUByte(16).toByte() }
    .toByteArray()

class CborDeserializerSuccessTest {
    @Test
    fun `atomic - undefined`() {
        val payload = "0xf7".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeNull()
        assertEquals(null, result)
    }

    @Test
    fun `atomic - float64 - 1dot625`() {
        val payload = "0xfb3ffa000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeDouble()
        assertEquals(1.625, result)
    }

    @Test
    fun `atomic - uint - 0 - max`() {
        val payload = "0x17".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt()
        assertEquals(23, result)
    }

    @Test
    fun `atomic - uint - 8 - min`() {
        val payload = "0x1b0000000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeLong().toULong()
        assertEquals(0uL, result)
    }

    @Test
    fun `atomic - uint - 8 - max`() {
        val payload = "0x1bffffffffffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeLong().toULong()
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `atomic - negint - 8 - min`() {
        val payload = "0x3b0000000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeLong()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - true`() {
        val payload = "0xf5".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeBoolean()
        assertEquals(true, result)
    }

    @Test
    fun `atomic - uint - 4 - min`() {
        val payload = "0x1a00000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt()
        assertEquals(0, result)
    }

    @Test
    fun `atomic - uint - 4 - max`() {
        val payload = "0x1affffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt().toUInt()
        assertEquals(UInt.MAX_VALUE, result)
    }

    @Test
    fun `atomic - negint - 1 - min`() {
        val payload = "0x3800".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - float16 - subnormal`() {
        val payload = "0xf90050".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        val result = deserializer.deserializeFloat()

        assertEquals(4.7683716E-6f, result)
    }

    @Test
    fun `atomic - float16 - NaN - LSB`() {
        val payload = "0xf97c01".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        val result = deserializer.deserializeFloat()

        assertEquals(Float.NaN, result)
    }

    @Test
    fun `atomic - uint - 1 - min`() {
        val payload = "0x1800".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte().toUByte()
        assertEquals(UByte.MIN_VALUE, result)
    }

    @Test
    fun `atomic - negint - 0 - min`() {
        val payload = "0x20".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - float16 - -Inf`() {
        val payload = "0xf9fc00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(Float.NEGATIVE_INFINITY, result)
    }

    @Test
    fun `atomic - negint - 8 - max`() {
        val payload = "0x3bfffffffffffffffe".toByteArray()
        val buffer = SdkBuffer().apply { write(payload) }
        val result = Cbor.Encoding.NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `atomic - uint - 0 - min`() {
        val payload = "0x00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte().toUByte()
        assertEquals(0u, result)
    }

    @Test
    fun `atomic - uint - 1 - max`() {
        val payload = "0x18ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte().toUByte()
        assertEquals(255u, result)
    }

    @Test
    fun `atomic - uint - 2 - min`() {
        val payload = "0x190000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeShort().toUShort()
        assertEquals(0u, result)
    }

    @Test
    fun `atomic - negint - 1 - max`() {
        val payload = "0x38ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeShort()
        assertEquals(-256, result)
    }

    @Test
    fun `atomic - negint - 2 - min`() {
        val payload = "0x390000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeShort()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - float64 - +Inf`() {
        val payload = "0xfb7ff0000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeDouble()
        assertEquals(Double.fromBits(9218868437227405312), result)
    }

    @Test
    fun `atomic - negint - 4 - min`() {
        val payload = "0x3a00000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - negint - 4 - max`() {
        val payload = "0x3affffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeLong()
        val res: Long = -4294967296
        assertEquals(res, result)
    }

    @Test
    fun `atomic - float16 - NaN - MSB`() {
        val payload = "0xf97e00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(Float.NaN, result)
    }

    @Test
    fun `atomic - float32 - +Inf`() {
        val payload = "0xfa7f800000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(Float.POSITIVE_INFINITY, result)
    }

    @Test
    fun `atomic - uint - 2 - max`() {
        val payload = "0x19ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeShort().toUShort()
        assertEquals(UShort.MAX_VALUE, result)
    }

    @Test
    fun `atomic - negint - 2 - max`() {
        val payload = "0x39ffff".toByteArray()
        val buffer = SdkBuffer().apply { write(payload) }
        val result = Cbor.Encoding.NegInt.decode(buffer).value
        assertEquals(65536u, result)
    }

    @Test
    fun `atomic - false`() {
        val payload = "0xf4".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeBoolean()
        assertEquals(false, result)
    }

    @Test
    fun `atomic - null`() {
        val payload = "0xf6".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeNull()
        assertEquals(null, result)
    }

    @Test
    fun `atomic - negint - 0 - max`() {
        val payload = "0x37".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte()
        assertEquals(-24, result)
    }

    @Test
    fun `atomic - float16 - +Inf`() {
        val payload = "0xf97c00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(Float.POSITIVE_INFINITY, result)
    }

    @Test
    fun `atomic - float32 - 1dot625`() {
        val payload = "0xfa3fd00000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(1.625f, result)
    }

    @Test
    fun `definite slice - len = 0`() {
        val payload = "0x40".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()
        assertEquals(0, result.size)
    }

    @Test
    fun `definite slice - len greater than 0`() {
        val payload = "0x43666f6f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()

        val expectedBytes = byteArrayOf(102, 111, 111)
        expectedBytes.forEachIndexed { index, byte ->
            assertEquals(byte, result[index])
        }
        assertEquals(3, result.size)
    }

    @Test
    fun `definite string - len = 0`() {
        val payload = "0x60".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("", result)
    }

    @Test
    fun `definite string - len greater than 0`() {
        val payload = "0x63666f6f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("foo", result)
    }

    @Test
    fun `indefinite slice - len greater than 0`() {
        val payload = "0x5f43666f6f40ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()

        val expectedBytes = byteArrayOf(102, 111, 111)
        expectedBytes.forEachIndexed { index, byte ->
            assertEquals(byte, result[index])
        }
        assertEquals(3, result.size)
    }

    @Test
    fun `indefinite slice - len greater than 0 - len greater than 0`() {
        val payload = "0x5f43666f6f43666f6fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()

        val expected = byteArrayOf(102, 111, 111, 102, 111, 111)
        expected.forEachIndexed { index, byte -> assertEquals(byte, result[index]) }
        assertEquals(expected.size, result.size)
    }

    @Test
    fun `indefinite slice - len = 0`() {
        val payload = "0x5fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()
        assertEquals(0, result.size)
    }

    @Test
    fun `indefinite slice - len = 0 explicit`() {
        val payload = "0x5f40ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()
        assertEquals(0, result.size)
    }

    @Test
    fun `indefinite slice - len = 0 - len greater than 0`() {
        val payload = "0x5f4043666f6fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()

        val expected = byteArrayOf(102, 111, 111)
        expected.forEachIndexed { index, byte -> assertEquals(byte, result[index]) }
        assertEquals(expected.size, result.size)
    }

    @Test
    fun `indefinite string - len = 0`() {
        val payload = "0x7fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("", result)
    }

    @Test
    fun `indefinite string - len = 0 - explicit`() {
        val payload = "0x7f60ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("", result)
    }

    @Test
    fun `indefinite string - len = 0 - len greater than 0`() {
        val payload = "0x7f6063666f6fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("foo", result)
    }

    @Test
    fun `indefinite string - len greater than 0 - len = 0`() {
        val payload = "0x7f63666f6f60ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("foo", result)
    }

    @Test
    fun `indefinite string - len greater than 0 - len greater than 0`() {
        val payload = "0x7f63666f6f63666f6fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("foofoo", result)
    }

    @Test
    fun `list of one uint - 1 - max`() {
        val payload = "0x8118ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeByte().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(255u, actual[0])
    }

    @Test
    fun `list of one uint - 8 - min`() {
        val payload = "0x811b0000000000000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Long>()
            while (hasNextElement()) {
                list.add(deserializeLong())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(0, actual[0])
    }

    @Test
    fun `indefinite list of uint - 1 - min`() {
        val payload = "0x9f1800ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeByte().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(0u, actual[0])
    }

    @Test
    fun `indefinite list of uint - 2 - max`() {
        val payload = "0x9f19ffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UShort>()
            while (hasNextElement()) {
                list.add(deserializeShort().toUShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UShort.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of negint - 2 - min`() {
        val payload = "0x9f390000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Short>()
            while (hasNextElement()) {
                list.add(deserializeShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `list of uint - 4 - max`() {
        val payload = "0x811affffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UInt>()
            while (hasNextElement()) {
                list.add(deserializeInt().toUInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UInt.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of uint - 8 - min`() {
        val payload = "0x9f1b0000000000000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<ULong>()
            while (hasNextElement()) {
                list.add(deserializeLong().toULong())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(ULong.MIN_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of negint - 2 - max`() {
        val payload = "0x9f39ffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Int>()
            while (hasNextElement()) {
                list.add(deserializeInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-65536, actual[0])
    }

    @Test
    fun `indefinite list of float16 - NaN - LSB`() {
        val payload = "0x9ff97c01ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.NaN, actual[0])
    }

    @Test
    fun `list of negint - 1 - max`() {
        val payload = "0x8138ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Short>()
            while (hasNextElement()) {
                list.add(deserializeShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-256, actual[0])
    }

    @Test
    fun `list of negint - 2 - min`() {
        val payload = "0x81390000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Short>()
            while (hasNextElement()) {
                list.add(deserializeShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `list of null`() {
        val payload = "0x81f6".toByteArray()

        val deserializer = CborDeserializer(payload)
        deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            while (hasNextElement()) {
                assertFalse(nextHasValue())
                deserializeNull()
            }
        }
    }

    @Test
    fun `list of float16 -Inf`() {
        val payload = "0x81f9fc00".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.NEGATIVE_INFINITY, actual[0])
    }

    @Test
    fun `indefinite list of uint - 4 - min`() {
        val payload = "0x9f1a00000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UInt>()
            while (hasNextElement()) {
                list.add(deserializeInt().toUInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UInt.MIN_VALUE, actual[0])
    }

    @Test
    fun `list of uint - 1 - min`() {
        val payload = "0x811800".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeByte().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UByte.MIN_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of uint - 0 - max`() {
        val payload = "0x9f17ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeByte().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(23u, actual[0])
    }

    @Test
    fun `indefinite list of negint - 0 - min`() {
        val payload = "0x9f20ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Byte>()
            while (hasNextElement()) {
                list.add(deserializeByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `indefinite list of negint - 1 - max`() {
        val payload = "0x9f38ffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Short>()
            while (hasNextElement()) {
                list.add(deserializeShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-256, actual[0])
    }

    @Test
    fun `indefinite list of null`() {
        val payload = "0x9ff6ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            while (hasNextElement()) {
                assertFalse(nextHasValue())
                deserializeNull()
            }
        }
    }

    @Test
    fun `indefinite list of uint - 1 - max`() {
        val payload = "0x9f18ffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeByte().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UByte.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of uint - 4 - max`() {
        val payload = "0x9f1affffffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UInt>()
            while (hasNextElement()) {
                list.add(deserializeInt().toUInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UInt.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of _ uint - 8 - max`() {
        val payload = "0x9f1bffffffffffffffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<ULong>()
            while (hasNextElement()) {
                list.add(deserializeLong().toULong())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(ULong.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of boolean true`() {
        val payload = "0x9ff5ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Boolean>()
            while (hasNextElement()) {
                list.add(deserializeBoolean())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(true, actual[0])
    }

    @Test
    fun `indefinite list of undefined`() {
        val payload = "0x9ff7ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Boolean>()
            while (hasNextElement()) {
                assertFalse(nextHasValue())
                deserializeNull()
            }
            return@deserializeList list
        }

        assertEquals(0, actual.size)
    }

    @Test
    fun `list of uint - 0 - max`() {
        val payload = "0x8117".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeByte().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(23u, actual[0])
    }

    @Test
    fun `list of uint - 8 - max`() {
        val payload = "0x811bffffffffffffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<ULong>()
            while (hasNextElement()) {
                list.add(deserializeLong().toULong())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(ULong.MAX_VALUE, actual[0])
    }

    @Test
    fun `list of negint - 0 - min`() {
        val payload = "0x8120".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Int>()
            while (hasNextElement()) {
                list.add(deserializeInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `list of negint - 0 - max`() {
        val payload = "0x8137".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Int>()
            while (hasNextElement()) {
                list.add(deserializeInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-24, actual[0])
    }

    @Test
    fun `list of negint - 4 - min`() {
        val payload = "0x813a00000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Int>()
            while (hasNextElement()) {
                list.add(deserializeInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `list of boolean true`() {
        val payload = "0x81f5".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Boolean>()
            while (hasNextElement()) {
                list.add(deserializeBoolean())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(true, actual[0])
    }

    @Test
    fun `list of float32`() {
        val payload = "0x81fa7f800000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.fromBits(2139095040), actual[0])
    }

    @Test
    fun `list of float64`() {
        val payload = "0x81fb7ff0000000000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Double>()
            while (hasNextElement()) {
                list.add(deserializeDouble())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Double.fromBits(9218868437227405312), actual[0])
    }

    @Test
    fun `indefinite list of uint - 2 - min`() {
        val payload = "0x9f190000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UShort>()
            while (hasNextElement()) {
                list.add(deserializeShort().toUShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UShort.MIN_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of float16 - NaN - MSB`() {
        val payload = "0x9ff97e00ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.NaN, actual[0])
    }

    @Test
    fun `indefinite list of negint - 0 - max`() {
        val payload = "0x9f37ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Byte>()
            while (hasNextElement()) {
                list.add(deserializeByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-24, actual[0])
    }

    @Test
    fun `indefinite list of negint - 1 - min`() {
        val payload = "0x9f3800ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Byte>()
            while (hasNextElement()) {
                list.add(deserializeByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `indefinite list of negint - 8 - min`() {
        val payload = "0x9f3b0000000000000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Long>()
            while (hasNextElement()) {
                list.add(deserializeLong())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `indefinite list of negint - 8 - max`() {
        val payload = "0x9f3bfffffffffffffffeff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Long>()
            while (hasNextElement()) {
                list.add(deserializeLong())
            }
            return@deserializeList list
        }
        assertEquals(1, actual.size)

        val remainingBuffer = "0x3bfffffffffffffffe".toByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = Cbor.Encoding.NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `list of boolean false`() {
        val payload = "0x81f4".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Boolean>()
            while (hasNextElement()) {
                list.add(deserializeBoolean())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(false, actual[0])
    }

    @Test
    fun `indefinite list of uint - 0 - min`() {
        val payload = "0x9f00ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Byte>()
            while (hasNextElement()) {
                list.add(deserializeByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(0, actual[0])
    }

    @Test
    fun `indefinite list of negint - 4 - min`() {
        val payload = "0x9f3a00000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Int>()
            while (hasNextElement()) {
                list.add(deserializeInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `indefinite list of negint - 4 - max`() {
        val payload = "0x9f3affffffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Long>()
            while (hasNextElement()) {
                list.add(deserializeLong())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-4294967296, actual[0])
    }

    @Test
    fun `indefinite list of float16 - +Inf`() {
        val payload = "0x9ff97c00ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.POSITIVE_INFINITY, actual[0])
    }

    @Test
    fun `list of uint - 0 - min`() {
        val payload = "0x8100".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeByte().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(0u, actual[0])
    }

    @Test
    fun `list of negint - 1 - min`() {
        val payload = "0x813800".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Byte>()
            while (hasNextElement()) {
                list.add(deserializeByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `indefinite list of float16 - -Inf`() {
        val payload = "0x9ff9fc00ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.NEGATIVE_INFINITY, actual[0])
    }

    @Test
    fun `indefinite list of float32`() {
        val payload = "0x9ffa7f800000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.fromBits(2139095040), actual[0])
    }

    @Test
    fun `list of uint - 2 - min`() {
        val payload = "0x81190000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UShort>()
            while (hasNextElement()) {
                list.add(deserializeShort().toUShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UShort.MIN_VALUE, actual[0])
    }

    @Test
    fun `list of uint - 4 - min`() {
        val payload = "0x811a00000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UInt>()
            while (hasNextElement()) {
                list.add(deserializeInt().toUInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UInt.MIN_VALUE, actual[0])
    }

    @Test
    fun `list of float16 - +Inf`() {
        val payload = "0x81f97c00".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.POSITIVE_INFINITY, actual[0])
    }

    @Test
    fun `indefinite list of float64`() {
        val payload = "0x9ffb7ff0000000000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Double>()
            while (hasNextElement()) {
                list.add(deserializeDouble())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Double.fromBits(9218868437227405312), actual[0])
    }

    @Test
    fun `list of float16 - NaN - MSB`() {
        val payload = "0x81f97e00".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.NaN, actual[0])
    }

    @Test
    fun `list of float16 - NaN - LSB`() {
        val payload = "0x81f97c01".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Float>()
            while (hasNextElement()) {
                list.add(deserializeFloat())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(Float.NaN, actual[0])
    }

    @Test
    fun `indefinite list of boolean false`() {
        val payload = "0x9ff4ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Boolean>()
            while (hasNextElement()) {
                list.add(deserializeBoolean())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(false, actual[0])
    }

    @Test
    fun `list of negint - 8 - min`() {
        val payload = "0x813b0000000000000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Long>()
            while (hasNextElement()) {
                list.add(deserializeLong())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-1, actual[0])
    }

    @Test
    fun `list of negint - 8 - max`() {
        val payload = "0x813bfffffffffffffffe".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<ULong>()
            while (hasNextElement()) {
                list.add(deserializeLong().toULong())
            }
            return@deserializeList list
        }
        assertEquals(1, actual.size)

        val remainingBuffer = "0x3bfffffffffffffffe".toByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = Cbor.Encoding.NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `list of undefined`() {
        val payload = "0x81f7".toByteArray()

        val deserializer = CborDeserializer(payload)
        deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            while (hasNextElement()) {
                assertFalse(nextHasValue())
                deserializeNull()
            }
        }
    }

    @Test
    fun `list of uint - 2 - max`() {
        val payload = "0x8119ffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UShort>()
            while (hasNextElement()) {
                list.add(deserializeShort().toUShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UShort.MAX_VALUE, actual[0])
    }

    @Test
    fun `list of negint - 2 - max`() {
        val payload = "0x8139ffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Int>()
            while (hasNextElement()) {
                list.add(deserializeInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-65536, actual[0])
    }

    @Test
    fun `list of negint - 4 - max`() {
        val payload = "0x813affffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Long>()
            while (hasNextElement()) {
                list.add(deserializeLong())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(-4294967296, actual[0])
    }

    @Test
    fun `map - _ uint - 8 - max`() {
        val payload = "0xbf63666f6f1bffffffffffffffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, ULong>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toULong()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(18446744073709551615u, actual.entries.first().value)
    }

    @Test
    fun `map of null`() {
        val payload = "0xa163666f6ff6".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Nothing?>()
            while (hasNextEntry()) {
                map[key()] = deserializeNull()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(null, actual.entries.first().value)
    }

    @Test
    fun `map - _ negint - 4 - max`() {
        val payload = "0xbf63666f6f3affffffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Long>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-4294967296, actual.entries.first().value)
    }

    @Test
    fun `map - _ float16 - -Inf`() {
        val payload = "0xbf63666f6ff9fc00ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.NEGATIVE_INFINITY, actual.entries.first().value)
    }

    @Test
    fun `map - uint - 2 - max`() {
        val payload = "0xa163666f6f19ffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UShort>()
            while (hasNextEntry()) {
                map[key()] = deserializeShort().toUShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UShort.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map - negint - 1 - min`() {
        val payload = "0xa163666f6f3800".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of undefined`() {
        val payload = "0xbf63666f6ff7ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Nothing?>()
            while (hasNextEntry()) {
                map[key()] = deserializeNull()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(null, actual.entries.first().value)
    }

    @Test
    fun `map - uint - 0 - max`() {
        val payload = "0xa163666f6f17".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Byte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(23, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 0 - max`() {
        val payload = "0xbf63666f6f17ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Byte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(23, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 1 - min`() {
        val payload = "0xbf63666f6f1800ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UByte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte().toUByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UByte.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 8 - min`() {
        val payload = "0xbf63666f6f1b0000000000000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, ULong>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toULong()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(ULong.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 8 - max`() {
        val payload = "0xbf63666f6f3bfffffffffffffffeff".toByteArray()

        val deserializer = CborDeserializer(payload)

        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, ULong>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toULong()
            }
            return@deserializeMap map
        }
        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)

        val remainingBuffer = "0x3bfffffffffffffffe".toByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = Cbor.Encoding.NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `map - uint - 2 - min`() {
        val payload = "0xa163666f6f190000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UShort>()
            while (hasNextEntry()) {
                map[key()] = deserializeShort().toUShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UShort.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of float16 - NaN - MSB`() {
        val payload = "0xbf63666f6ff97e00ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.NaN, actual.entries.first().value)
    }

    @Test
    fun `map - negint - 0 - min`() {
        val payload = "0xa163666f6f20".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `map - float16 - -Inf`() {
        val payload = "0xa163666f6ff9fc00".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.NEGATIVE_INFINITY, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 1 - max`() {
        val payload = "0xbf63666f6f38ffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Short>()
            while (hasNextEntry()) {
                map[key()] = deserializeShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-256, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 8 - min`() {
        val payload = "0xbf63666f6f3b0000000000000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Long>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `map - uint - 1 - min`() {
        val payload = "0xa163666f6f1800".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UByte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte().toUByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UByte.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 2 - min`() {
        val payload = "0xbf63666f6f190000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UShort>()
            while (hasNextEntry()) {
                map[key()] = deserializeShort().toUShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UShort.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 2 - max`() {
        val payload = "0xbf63666f6f19ffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UShort>()
            while (hasNextEntry()) {
                map[key()] = deserializeShort().toUShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UShort.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 0 - max`() {
        val payload = "0xbf63666f6f37ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Byte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-24, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 2 - max`() {
        val payload = "0xbf63666f6f39ffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-65536, actual.entries.first().value)
    }

    @Test
    fun `map of boolean true`() {
        val payload = "0xa163666f6ff5".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Boolean>()
            while (hasNextEntry()) {
                map[key()] = deserializeBoolean()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(true, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of boolean true`() {
        val payload = "0xbf63666f6ff5ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Boolean>()
            while (hasNextEntry()) {
                map[key()] = deserializeBoolean()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(true, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of boolean false`() {
        val payload = "0xbf63666f6ff4ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Boolean>()
            while (hasNextEntry()) {
                map[key()] = deserializeBoolean()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(false, actual.entries.first().value)
    }

    @Test
    fun `map - uint - 8 - max`() {
        val payload = "0xa163666f6f1bffffffffffffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, ULong>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toULong()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(ULong.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map - float16 - NaN - LSB`() {
        val payload = "0xa163666f6ff97c01".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.NaN, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 0 - min`() {
        val payload = "0xbf63666f6f00ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UInt>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt().toUInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UInt.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 4 - min`() {
        val payload = "0xbf63666f6f3a00000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of float32`() {
        val payload = "0xbf63666f6ffa7f800000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.fromBits(2139095040), actual.entries.first().value)
    }

    @Test
    fun `map of uint - 0 - min`() {
        val payload = "0xa163666f6f00".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UByte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte().toUByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UByte.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map - negint - 1 - max`() {
        val payload = "0xa163666f6f38ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Short>()
            while (hasNextEntry()) {
                map[key()] = deserializeShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-256, actual.entries.first().value)
    }

    @Test
    fun `map - float64`() {
        val payload = "0xa163666f6ffb7ff0000000000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Double>()
            while (hasNextEntry()) {
                map[key()] = deserializeDouble()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Double.fromBits(9218868437227405312), actual.entries.first().value)
    }

    @Test
    fun `indefinite map of float16 - NaN - LSB`() {
        val payload = "0xbf63666f6ff97c01ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.NaN, actual.entries.first().value)
    }

    @Test
    fun `map - uint - 8 - min`() {
        val payload = "0xa163666f6f1b0000000000000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, ULong>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toULong()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(ULong.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map - negint - 8 - max`() {
        val payload = "0xa163666f6f3bfffffffffffffffe".toByteArray()

        val deserializer = CborDeserializer(payload)

        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Long>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong()
            }
            return@deserializeMap map
        }
        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)

        val remainingBuffer = "0x3bfffffffffffffffe".toByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = Cbor.Encoding.NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `map of undefined`() {
        val payload = "0xa163666f6ff7".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Nothing?>()
            while (hasNextEntry()) {
                map[key()] = deserializeNull()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(null, actual.entries.first().value)
    }

    @Test
    fun `map of float16 - NaN - MSB`() {
        val payload = "0xa163666f6ff97e00".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.NaN, actual.entries.first().value)
    }

    @Test
    fun `map of negint - 8 - min`() {
        val payload = "0xa163666f6f3b0000000000000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Long>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 4 - max`() {
        val payload = "0xbf63666f6f1affffffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UInt>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt().toUInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UInt.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 1 - min`() {
        val payload = "0xbf63666f6f3800ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Byte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of float16 - +Inf`() {
        val payload = "0xbf63666f6ff97c00ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.POSITIVE_INFINITY, actual.entries.first().value)
    }

    @Test
    fun `map - negint - 2 - min`() {
        val payload = "0xa163666f6f390000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Short>()
            while (hasNextEntry()) {
                map[key()] = deserializeShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `map of false`() {
        val payload = "0xa163666f6ff4".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Boolean>()
            while (hasNextEntry()) {
                map[key()] = deserializeBoolean()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(false, actual.entries.first().value)
    }

    @Test
    fun `map of float32`() {
        val payload = "0xa163666f6ffa7f800000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.fromBits(2139095040), actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 1 - max`() {
        val payload = "0xbf63666f6f18ffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UByte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte().toUByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UByte.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map of negint - 0 - max`() {
        val payload = "0xa163666f6f37".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Byte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-24, actual.entries.first().value)
    }

    @Test
    fun `map of negint - 4 - max`() {
        val payload = "0xa163666f6f3affffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Long>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-4294967296, actual.entries.first().value)
    }

    @Test
    fun `map of float16 - +Inf`() {
        val payload = "0xa163666f6ff97c00".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Float>()
            while (hasNextEntry()) {
                map[key()] = deserializeFloat()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Float.POSITIVE_INFINITY, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of float64`() {
        val payload = "0xbf63666f6ffb7ff0000000000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Double>()
            while (hasNextEntry()) {
                map[key()] = deserializeDouble()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(Double.fromBits(9218868437227405312), actual.entries.first().value)
    }

    @Test
    fun `map of uint - 1 - max`() {
        val payload = "0xa163666f6f18ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UByte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte().toUByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UByte.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map - uint - 4 - max`() {
        val payload = "0xa163666f6f1affffffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UInt>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt().toUInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UInt.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map of negint - 2 - max`() {
        val payload = "0xa163666f6f39ffff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-65536, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of uint - 4 - min`() {
        val payload = "0xbf63666f6f1a00000000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UInt>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt().toUInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UInt.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 0 - min`() {
        val payload = "0xbf63666f6f20ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Byte>()
            while (hasNextEntry()) {
                map[key()] = deserializeByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of null`() {
        val payload = "0xbf63666f6ff6ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Nothing?>()
            while (hasNextEntry()) {
                map[key()] = deserializeNull()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(null, actual.entries.first().value)
    }

    @Test
    fun `map of uint - 4 - min`() {
        val payload = "0xa163666f6f1a00000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UInt>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt().toUInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UInt.MIN_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map of negint - 4 - min`() {
        val payload = "0xa163666f6f3a00000000".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializeInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 2 - min`() {
        val payload = "0xbf63666f6f390000ff".toByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Short>()
            while (hasNextEntry()) {
                map[key()] = deserializeShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(-1, actual.entries.first().value)
    }
}
