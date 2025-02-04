/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.IgnoreNative
import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalStdlibApi::class)
class CborSerializerTest {
    @Test
    fun testBoolean() {
        val tests = listOf(true, false, true, false, false)
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeBoolean(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it, deserializer.deserializeBoolean())
        }
        assertEquals(0, buffer.size)
    }

    @Test
    fun testByte() {
        val tests = listOf(Byte.MIN_VALUE, -34, 0, 39, Byte.MAX_VALUE)
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeByte(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        tests.forEach {
            assertEquals(it, deserializer.deserializeByte())
        }
        assertEquals(0, buffer.size)
    }

    @Test
    fun testChar() {
        val tests = listOf(
            'a', 'z', 'h', 'e', 'l', 'l', 'o',
            'A', 'Z', 'H', 'E', 'L', 'L', 'O',
            '1', '2', '3', '4', '5', '6', '7',
            '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
            '\n', '\t', '\r', ' ',
        )
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeChar(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it.toString(), deserializer.deserializeString())
        }
        assertEquals(0, buffer.size)
    }

    @Test
    fun testInt() {
        val tests = listOf(Int.MIN_VALUE, -34, 0, 39, 402, Int.MAX_VALUE)
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeInt(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        tests.forEach {
            assertEquals(it, deserializer.deserializeInt())
        }
        assertEquals(0, buffer.size)
    }

    @Test
    fun testLong() {
        val tests = listOf(Long.MIN_VALUE, -34, 0, 39, 402, Long.MAX_VALUE)
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeLong(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        tests.forEach {
            assertEquals(it, deserializer.deserializeLong())
        }
        assertEquals(0, buffer.size)
    }

    @Test
    fun testFloat() {
        val tests = listOf(
            Float.NaN,
            Float.NEGATIVE_INFINITY,
            Float.MIN_VALUE,
            123.456f,
            0.00432f,
            0.235f,
            3.141592f,
            6.2831855f,
            2.71828f,
            Float.MAX_VALUE,
            Float.POSITIVE_INFINITY,
        )

        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeFloat(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it, deserializer.deserializeFloat())
        }
        assertEquals(0, buffer.size)
    }

    @Test
    fun testDouble() {
        val tests = listOf(
            Double.NaN,
            Double.NEGATIVE_INFINITY,
            Double.MIN_VALUE,
            0.000000000000000000000000000000000000000000001,
            123.456,
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY,
        )

        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeDouble(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it, deserializer.deserializeDouble())
        }
        assertEquals(0, buffer.size)
    }

    @Test
    fun testBigInteger() {
        val tests = listOf(
            BigInteger("-32134902384590238490284023839028330923830129830129301234239834982"),
            BigInteger("-500"),
            BigInteger("0"),
            BigInteger("500"),
            BigInteger("492303248902358239048230948902382390849385039583459348509238402384347238547238947"),
        )

        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeBigInteger(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it, deserializer.deserializeBigInteger())
        }

        // Test taken from the CBOR RFC: https://www.rfc-editor.org/rfc/rfc8949.html#name-bignums
        serializer.serializeBigInteger(BigInteger("18446744073709551616"))

        assertEquals("c249010000000000000000", serializer.toByteArray().toHexString())

        assertEquals(0, buffer.size)
    }

    @Test
    fun testBigDecimal() {
        val tests = listOf<BigDecimal>(
            BigDecimal("-123453450934503474823945734895.4563458734895738978902384902384908"),
            BigDecimal("-123.456"),
            BigDecimal("-0.000000000000000000000000000000000000000000000000000000000000000000000000000000000001"),
            BigDecimal("-0.0001"),
            BigDecimal("-0.01"),
            BigDecimal("0"),
            BigDecimal("0.01"),
            BigDecimal("0.0001"),
            BigDecimal("0.000000000000000000000000000000000000000000000000000000000000000000000000000000000001"),
            BigDecimal("123.456"),
            BigDecimal("392.456573489578934759384750983745980237590872350"),
            BigDecimal(".01"),
            BigDecimal(".0"),
            BigDecimal("13"),
            BigDecimal(".439328490382490832409823409234324723895732984572389472389472398472398472398472"),
        )

        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeBigDecimal(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it, deserializer.deserializeBigDecimal())
        }
        assertEquals(0, buffer.size)

        // Test taken from CBOR RFC: https://www.rfc-editor.org/rfc/rfc8949.html#section-3.4.4
        serializer.serializeBigDecimal(BigDecimal("273.15"))
        assertEquals("c48221196ab3", serializer.toByteArray().toHexString())
    }

    @Test
    fun testString() {
        val tests = listOf(
            "",
            "abc",
            "fhhr09u32094mujdvsokjv,oshjx9i,u4390ru!",
            "!@#@#$%$^%&**(%^^%&%^&_$%)#\$_%@(#$)(!@-03-203-02-3402-34\r\n\t",
            "null",
            "124",
        )

        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeString(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it, deserializer.deserializeString())
        }
        assertEquals(0, buffer.size)
    }

    @Test
    fun testInstant() {
        val tests = listOf(
            Instant.MIN_VALUE,
            Instant.fromEpochSeconds(0) - 1825.days,
            Instant.fromEpochSeconds(0) - 365.days,
            Instant.fromEpochSeconds(0) - 5.days,
            Instant.fromEpochSeconds(0) - 5.seconds,
            Instant.fromEpochSeconds(0),
            Instant.now() - 10.days,
            Instant.now(),
            Instant.now() + 10.days,
            Instant.now() + 365.days,
            Instant.now() + 1825.days,
        )

        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeInstant(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it.epochMilliseconds, deserializer.deserializeInstant(TimestampFormat.EPOCH_SECONDS).epochMilliseconds)
        }

        assertEquals(0, buffer.size)
    }

    @Test
    fun testNull() {
        val serializer = CborSerializer()

        serializer.serializeNull()
        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertNull(deserializer.deserializeNull())
    }

    @Test
    fun testDocument() {
        val serializer = CborSerializer()
        assertFailsWith<SerializationException> {
            serializer.serializeDocument(null)
        }
    }

    @Test
    fun testList() {
        val serializer = CborSerializer()

        serializer.serializeList(SdkFieldDescriptor(SerialKind.List)) {
            serializeNull()
            serializeFloat(143.434f)
            serializeInt(Int.MIN_VALUE)
            serializeLong(Long.MAX_VALUE)
            serializeBoolean(true)
            serializeChar('a')
            serializeChar('z')
            serializeString("bye!")
            endList()
        }

        val bytes = serializer.toByteArray()
        val deserializer = CborDeserializer(bytes)

        deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
            deserializeNull()
            assertEquals(143.434f, deserializeFloat())
            assertEquals(Int.MIN_VALUE, deserializeInt())
            assertEquals(Long.MAX_VALUE, deserializeLong())
            assertTrue(deserializeBoolean())
            assertEquals("a", deserializeString())
            assertEquals("z", deserializeString())
            assertEquals("bye!", deserializeString())

            // end of list
            assertFails {
                deserializeInt()
            }
        }
    }

    @Test
    fun testMap() {
        val serializer = CborSerializer()

        serializer.serializeMap(SdkFieldDescriptor(SerialKind.List)) {
            entry("float", 143.434f)
            entry("int", Int.MIN_VALUE)
            entry("long", Long.MAX_VALUE)
            entry("boolean", true)
            entry("charA", 'a')
            entry("charZ", 'z')
            entry("string", "bye!")
            entry("timestamp", Instant.now(), TimestampFormat.EPOCH_SECONDS)
            endMap()
        }

        val bytes = serializer.toByteArray()
        val deserializer = CborDeserializer(bytes)

        deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
            assertEquals("float", deserializeString())
            assertEquals(143.434f, deserializeFloat())

            assertEquals("int", deserializeString())
            assertEquals(Int.MIN_VALUE, deserializeInt())

            assertEquals("long", deserializeString())
            assertEquals(Long.MAX_VALUE, deserializeLong())

            assertEquals("boolean", deserializeString())
            assertEquals(true, deserializeBoolean())

            assertEquals("charA", deserializeString())
            assertEquals("a", deserializeString())

            assertEquals("charZ", deserializeString())
            assertEquals("z", deserializeString())

            assertEquals("string", deserializeString())
            assertEquals("bye!", deserializeString())

            // end of list
            assertFails {
                deserializeInt()
            }
        }
    }
}
