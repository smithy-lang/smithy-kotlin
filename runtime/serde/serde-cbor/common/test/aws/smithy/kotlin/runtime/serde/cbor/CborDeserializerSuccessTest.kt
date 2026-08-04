/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.cbor.encoding.NegInt
import aws.smithy.kotlin.runtime.serde.deserializeList
import aws.smithy.kotlin.runtime.serde.deserializeMap
import kotlin.test.*

class CborDeserializerSuccessTest {
    @Test
    fun `atomic - undefined`() {
        val payload = "f7".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeNull()
        assertEquals(null, result)
    }

    @Test
    fun `atomic - float64 - 1dot625`() {
        val payload = "fb3ffa000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeDouble()
        assertEquals(1.625, result)
    }

    @Test
    fun `atomic - uint - 0 - max`() {
        val payload = "17".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt()
        assertEquals(23, result)
    }

    @Test
    fun `atomic - uint - 8 - min`() {
        val payload = "1b0000000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeLong().toULong()
        assertEquals(0uL, result)
    }

    @Test
    fun `atomic - uint - 8 - max`() {
        val payload = "1bffffffffffffffff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        assertEquals(ULong.MAX_VALUE, aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt.decode(buffer).value)
    }

    @Test
    fun `atomic - negint - 8 - min`() {
        val payload = "3b0000000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeLong()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - true`() {
        val payload = "f5".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeBoolean()
        assertEquals(true, result)
    }

    @Test
    fun `atomic - uint - 4 - min`() {
        val payload = "1a00000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt()
        assertEquals(0, result)
    }

    @Test
    fun `atomic - uint - 4 - max`() {
        val payload = "1affffffff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertEquals(UInt.MAX_VALUE, aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt.decode(buffer).value.toUInt())
    }

    @Test
    fun `atomic - negint - 1 - min`() {
        val payload = "3800".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - float16 - subnormal`() {
        val payload = "f90050".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        val result = deserializer.deserializeFloat()

        assertEquals(4.7683716E-6f, result)
    }

    @Test
    fun `atomic - float16 - NaN - LSB`() {
        val payload = "f97c01".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        val result = deserializer.deserializeFloat()

        assertEquals(Float.NaN, result)
    }

    @Test
    fun `atomic - uint - 1 - min`() {
        val payload = "1800".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte().toUByte()
        assertEquals(UByte.MIN_VALUE, result)
    }

    @Test
    fun `atomic - negint - 0 - min`() {
        val payload = "20".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - float16 - -Inf`() {
        val payload = "f9fc00".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(Float.NEGATIVE_INFINITY, result)
    }

    @Test
    fun `atomic - negint - 8 - max`() {
        val payload = "3bfffffffffffffffe".hexToByteArray()
        val buffer = SdkBuffer().apply { write(payload) }
        val result = NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `atomic - uint - 0 - min`() {
        val payload = "00".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte().toUByte()
        assertEquals(0u, result)
    }

    @Test
    fun `atomic - uint - 1 - max`() {
        val payload = "18ff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        assertEquals(255u, aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt.decode(buffer).value)
    }

    @Test
    fun `atomic - uint - 2 - min`() {
        val payload = "190000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeShort().toUShort()
        assertEquals(0u, result)
    }

    @Test
    fun `atomic - negint - 1 - max`() {
        val payload = "38ff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeShort()
        assertEquals(-256, result)
    }

    @Test
    fun `atomic - negint - 2 - min`() {
        val payload = "390000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeShort()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - float64 - +Inf`() {
        val payload = "fb7ff0000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeDouble()
        assertEquals(Double.fromBits(9218868437227405312), result)
    }

    @Test
    fun `atomic - negint - 4 - min`() {
        val payload = "3a00000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeInt()
        assertEquals(-1, result)
    }

    @Test
    fun `atomic - negint - 4 - max`() {
        val payload = "3affffffff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeLong()
        val res: Long = -4294967296
        assertEquals(res, result)
    }

    @Test
    fun `atomic - float16 - NaN - MSB`() {
        val payload = "f97e00".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(Float.NaN, result)
    }

    @Test
    fun `atomic - float32 - +Inf`() {
        val payload = "fa7f800000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(Float.POSITIVE_INFINITY, result)
    }

    @Test
    fun `atomic - uint - 2 - max`() {
        val payload = "19ffff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        assertEquals(UShort.MAX_VALUE, aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt.decode(buffer).value.toUShort())
    }

    @Test
    fun `atomic - negint - 2 - max`() {
        val payload = "39ffff".hexToByteArray()
        val buffer = SdkBuffer().apply { write(payload) }
        val result = NegInt.decode(buffer).value
        assertEquals(65536u, result)
    }

    @Test
    fun `atomic - false`() {
        val payload = "f4".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeBoolean()
        assertEquals(false, result)
    }

    @Test
    fun `atomic - null`() {
        val payload = "f6".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeNull()
        assertEquals(null, result)
    }

    @Test
    fun `atomic - negint - 0 - max`() {
        val payload = "37".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByte()
        assertEquals(-24, result)
    }

    @Test
    fun `atomic - float16 - +Inf`() {
        val payload = "f97c00".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(Float.POSITIVE_INFINITY, result)
    }

    @Test
    fun `atomic - float32 - 1dot625`() {
        val payload = "fa3fd00000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeFloat()
        assertEquals(1.625f, result)
    }

    @Test
    fun `definite slice - len = 0`() {
        val payload = "40".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()
        assertEquals(0, result.size)
    }

    @Test
    fun `definite slice - len greater than 0`() {
        val payload = "43666f6f".hexToByteArray()

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
        val payload = "60".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("", result)
    }

    @Test
    fun `definite string - len greater than 0`() {
        val payload = "63666f6f".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("foo", result)
    }

    @Test
    fun `indefinite slice - len greater than 0`() {
        val payload = "5f43666f6f40ff".hexToByteArray()

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
        val payload = "5f43666f6f43666f6fff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()

        val expected = byteArrayOf(102, 111, 111, 102, 111, 111)
        expected.forEachIndexed { index, byte -> assertEquals(byte, result[index]) }
        assertEquals(expected.size, result.size)
    }

    @Test
    fun `indefinite slice - len = 0`() {
        val payload = "5fff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()
        assertEquals(0, result.size)
    }

    @Test
    fun `indefinite slice - len = 0 explicit`() {
        val payload = "5f40ff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()
        assertEquals(0, result.size)
    }

    @Test
    fun `indefinite slice - len = 0 - len greater than 0`() {
        val payload = "5f4043666f6fff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeByteArray()

        val expected = byteArrayOf(102, 111, 111)
        expected.forEachIndexed { index, byte -> assertEquals(byte, result[index]) }
        assertEquals(expected.size, result.size)
    }

    @Test
    fun `indefinite string - len = 0`() {
        val payload = "7fff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("", result)
    }

    @Test
    fun `indefinite string - len = 0 - explicit`() {
        val payload = "7f60ff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("", result)
    }

    @Test
    fun `indefinite string - len = 0 - len greater than 0`() {
        val payload = "7f6063666f6fff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("foo", result)
    }

    @Test
    fun `indefinite string - len greater than 0 - len = 0`() {
        val payload = "7f63666f6f60ff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("foo", result)
    }

    @Test
    fun `indefinite string - len greater than 0 - len greater than 0`() {
        val payload = "7f63666f6f63666f6fff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        val result = deserializer.deserializeString()
        assertEquals("foofoo", result)
    }

    @Test
    fun `list of one uint - 1 - max`() {
        val payload = "8118ff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeLong().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(255u, actual[0])
    }

    @Test
    fun `list of one uint - 8 - min`() {
        val payload = "811b0000000000000000".hexToByteArray()

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
        val payload = "9f1800ff".hexToByteArray()

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
        val payload = "9f19ffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UShort>()
            while (hasNextElement()) {
                list.add(deserializeLong().toUShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UShort.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of negint - 2 - min`() {
        val payload = "9f390000ff".hexToByteArray()

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
        val payload = "811affffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UInt>()
            while (hasNextElement()) {
                list.add(deserializeLong().toUInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UInt.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of uint - 8 - min`() {
        val payload = "9f1b0000000000000000ff".hexToByteArray()

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
        val payload = "9f39ffffff".hexToByteArray()

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
        val payload = "9ff97c01ff".hexToByteArray()

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
        val payload = "8138ff".hexToByteArray()

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
        val payload = "81390000".hexToByteArray()

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
        val payload = "81f6".hexToByteArray()

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
        val payload = "81f9fc00".hexToByteArray()

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
        val payload = "9f1a00000000ff".hexToByteArray()

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
        val payload = "811800".hexToByteArray()

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
        val payload = "9f17ff".hexToByteArray()

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
        val payload = "9f20ff".hexToByteArray()

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
        val payload = "9f38ffff".hexToByteArray()

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
        val payload = "9ff6ff".hexToByteArray()

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
        val payload = "9f18ffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UByte>()
            while (hasNextElement()) {
                list.add(deserializeLong().toUByte())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UByte.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of uint - 4 - max`() {
        val payload = "9f1affffffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UInt>()
            while (hasNextElement()) {
                list.add(deserializeLong().toUInt())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UInt.MAX_VALUE, actual[0])
    }

    @Test
    fun `indefinite list of _ uint - 8 - max`() {
        val payload = "9f1bffffffffffffffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<ULong>()
            while (hasNextElement()) {
                list.add(ULong.MAX_VALUE)
                break
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)

        val remainingBuffer = "1bffffffffffffffff".hexToByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `indefinite list of boolean true`() {
        val payload = "9ff5ff".hexToByteArray()

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
        val payload = "9ff7ff".hexToByteArray()

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
        val payload = "8117".hexToByteArray()

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
        val payload = "811bffffffffffffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<ULong>()
            while (hasNextElement()) {
                list.add(ULong.MAX_VALUE)
            }
            return@deserializeList list
        }
        assertEquals(1, actual.size)

        val remainingBuffer = "1bffffffffffffffff".hexToByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `list of negint - 0 - min`() {
        val payload = "8120".hexToByteArray()

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
        val payload = "8137".hexToByteArray()

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
        val payload = "813a00000000".hexToByteArray()

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
        val payload = "81f5".hexToByteArray()

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
        val payload = "81fa7f800000".hexToByteArray()

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
        val payload = "81fb7ff0000000000000".hexToByteArray()

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
        val payload = "9f190000ff".hexToByteArray()

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
        val payload = "9ff97e00ff".hexToByteArray()

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
        val payload = "9f37ff".hexToByteArray()

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
        val payload = "9f3800ff".hexToByteArray()

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
        val payload = "9f3b0000000000000000ff".hexToByteArray()

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
        val payload = "9f3bfffffffffffffffeff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<Long>()
            while (hasNextElement()) {
                list.add(Long.MAX_VALUE)
                break
            }
            return@deserializeList list
        }
        assertEquals(1, actual.size)

        val remainingBuffer = "3bfffffffffffffffe".hexToByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `list of boolean false`() {
        val payload = "81f4".hexToByteArray()

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
        val payload = "9f00ff".hexToByteArray()

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
        val payload = "9f3a00000000ff".hexToByteArray()

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
        val payload = "9f3affffffffff".hexToByteArray()

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
        val payload = "9ff97c00ff".hexToByteArray()

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
        val payload = "8100".hexToByteArray()

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
        val payload = "813800".hexToByteArray()

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
        val payload = "9ff9fc00ff".hexToByteArray()

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
        val payload = "9ffa7f800000ff".hexToByteArray()

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
        val payload = "81190000".hexToByteArray()

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
        val payload = "811a00000000".hexToByteArray()

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
        val payload = "81f97c00".hexToByteArray()

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
        val payload = "9ffb7ff0000000000000ff".hexToByteArray()

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
        val payload = "81f97e00".hexToByteArray()

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
        val payload = "81f97c01".hexToByteArray()

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
        val payload = "9ff4ff".hexToByteArray()

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
        val payload = "813b0000000000000000".hexToByteArray()

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
        val payload = "813bfffffffffffffffe".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<ULong>()
            while (hasNextElement()) {
                list.add(ULong.MAX_VALUE)
                break
            }
            return@deserializeList list
        }
        assertEquals(1, actual.size)

        val remainingBuffer = "3bfffffffffffffffe".hexToByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `list of undefined`() {
        val payload = "81f7".hexToByteArray()

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
        val payload = "8119ffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            val list = mutableListOf<UShort>()
            while (hasNextElement()) {
                list.add(deserializeLong().toUShort())
            }
            return@deserializeList list
        }

        assertEquals(1, actual.size)
        assertEquals(UShort.MAX_VALUE, actual[0])
    }

    @Test
    fun `list of negint - 2 - max`() {
        val payload = "8139ffff".hexToByteArray()

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
        val payload = "813affffffff".hexToByteArray()

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
        val payload = "bf63666f6f1bffffffffffffffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, ULong>()
            while (hasNextEntry()) {
                map[key()] = ULong.MAX_VALUE
                break
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)

        val remainingBuffer = "1bffffffffffffffffff".hexToByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `map of null`() {
        val payload = "a163666f6ff6".hexToByteArray()

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
        val payload = "bf63666f6f3affffffffff".hexToByteArray()

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
        val payload = "bf63666f6ff9fc00ff".hexToByteArray()

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
        val payload = "a163666f6f19ffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UShort>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toUShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UShort.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map - negint - 1 - min`() {
        val payload = "a163666f6f3800".hexToByteArray()

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
        val payload = "bf63666f6ff7ff".hexToByteArray()

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
        val payload = "a163666f6f17".hexToByteArray()

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
        val payload = "bf63666f6f17ff".hexToByteArray()

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
        val payload = "bf63666f6f1800ff".hexToByteArray()

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
        val payload = "bf63666f6f1b0000000000000000ff".hexToByteArray()

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
        val payload = "bf63666f6f3bfffffffffffffffeff".hexToByteArray()

        val deserializer = CborDeserializer(payload)

        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, ULong>()
            while (hasNextEntry()) {
                map[key()] = ULong.MIN_VALUE
                break
            }
            return@deserializeMap map
        }
        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)

        val remainingBuffer = "3bfffffffffffffffe".hexToByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `map - uint - 2 - min`() {
        val payload = "a163666f6f190000".hexToByteArray()

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
        val payload = "bf63666f6ff97e00ff".hexToByteArray()

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
        val payload = "a163666f6f20".hexToByteArray()

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
        val payload = "a163666f6ff9fc00".hexToByteArray()

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
        val payload = "bf63666f6f38ffff".hexToByteArray()

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
        val payload = "bf63666f6f3b0000000000000000ff".hexToByteArray()

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
        val payload = "a163666f6f1800".hexToByteArray()

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
        val payload = "bf63666f6f190000ff".hexToByteArray()

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
        val payload = "bf63666f6f19ffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UShort>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toUShort()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UShort.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 0 - max`() {
        val payload = "bf63666f6f37ff".hexToByteArray()

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
        val payload = "bf63666f6f39ffffff".hexToByteArray()

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
        val payload = "a163666f6ff5".hexToByteArray()

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
        val payload = "bf63666f6ff5ff".hexToByteArray()

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
        val payload = "bf63666f6ff4ff".hexToByteArray()

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
        val payload = "a163666f6f1bffffffffffffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, ULong>()
            while (hasNextEntry()) {
                map[key()] = ULong.MAX_VALUE
                break
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)

        val remainingBuffer = "1bffffffffffffffff".hexToByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `map - float16 - NaN - LSB`() {
        val payload = "a163666f6ff97c01".hexToByteArray()

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
        val payload = "bf63666f6f00ff".hexToByteArray()

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
        val payload = "bf63666f6f3a00000000ff".hexToByteArray()

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
        val payload = "bf63666f6ffa7f800000ff".hexToByteArray()

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
        val payload = "a163666f6f00".hexToByteArray()

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
        val payload = "a163666f6f38ff".hexToByteArray()

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
        val payload = "a163666f6ffb7ff0000000000000".hexToByteArray()

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
        val payload = "bf63666f6ff97c01ff".hexToByteArray()

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
        val payload = "a163666f6f1b0000000000000000".hexToByteArray()

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
        val payload = "a163666f6f3bfffffffffffffffe".hexToByteArray()

        val deserializer = CborDeserializer(payload)

        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, Long>()
            while (hasNextEntry()) {
                map[key()] = Long.MAX_VALUE
                break
            }
            return@deserializeMap map
        }
        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)

        val remainingBuffer = "3bfffffffffffffffe".hexToByteArray()
        val buffer = SdkBuffer().apply { write(remainingBuffer) }
        val result = NegInt.decode(buffer).value
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun `map of undefined`() {
        val payload = "a163666f6ff7".hexToByteArray()

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
        val payload = "a163666f6ff97e00".hexToByteArray()

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
        val payload = "a163666f6f3b0000000000000000".hexToByteArray()

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
        val payload = "bf63666f6f1affffffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UInt>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toUInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UInt.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `indefinite map of negint - 1 - min`() {
        val payload = "bf63666f6f3800ff".hexToByteArray()

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
        val payload = "bf63666f6ff97c00ff".hexToByteArray()

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
        val payload = "a163666f6f390000".hexToByteArray()

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
        val payload = "a163666f6ff4".hexToByteArray()

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
        val payload = "a163666f6ffa7f800000".hexToByteArray()

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
        val payload = "bf63666f6f18ffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UByte>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toUByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UByte.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map of negint - 0 - max`() {
        val payload = "a163666f6f37".hexToByteArray()

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
        val payload = "a163666f6f3affffffff".hexToByteArray()

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
        val payload = "a163666f6ff97c00".hexToByteArray()

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
        val payload = "bf63666f6ffb7ff0000000000000ff".hexToByteArray()

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
        val payload = "a163666f6f18ff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UByte>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toUByte()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UByte.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map - uint - 4 - max`() {
        val payload = "a163666f6f1affffffff".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        val actual = deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            val map = mutableMapOf<String, UInt>()
            while (hasNextEntry()) {
                map[key()] = deserializeLong().toUInt()
            }
            return@deserializeMap map
        }

        assertEquals(1, actual.size)
        assertEquals("foo", actual.entries.first().key)
        assertEquals(UInt.MAX_VALUE, actual.entries.first().value)
    }

    @Test
    fun `map of negint - 2 - max`() {
        val payload = "a163666f6f39ffff".hexToByteArray()

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
        val payload = "bf63666f6f1a00000000ff".hexToByteArray()

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
        val payload = "bf63666f6f20ff".hexToByteArray()

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
        val payload = "bf63666f6ff6ff".hexToByteArray()

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
        val payload = "a163666f6f1a00000000".hexToByteArray()

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
        val payload = "a163666f6f3a00000000".hexToByteArray()

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
        val payload = "bf63666f6f390000ff".hexToByteArray()

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
