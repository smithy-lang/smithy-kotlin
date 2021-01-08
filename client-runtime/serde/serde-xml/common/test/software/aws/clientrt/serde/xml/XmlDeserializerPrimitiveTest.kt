/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.DeserializationException
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.SdkObjectDescriptor
import software.aws.clientrt.serde.SerialKind
import software.aws.clientrt.testing.runSuspendTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerPrimitiveTest {
    @Test
    fun itHandlesDoubles() = runSuspendTest {
        val payload = "<node>1.2</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Double))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeDouble()!!
        val expected = 1.2
        assertTrue(abs(actual - expected) <= 0.0001)
    }

    @Test
    fun itHandlesFloats() = runSuspendTest {
        val payload = "<node>1.2</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Float))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeFloat()!!
        val expected = 1.2f
        assertTrue(abs(actual - expected) <= 0.0001f)
    }

    @Test
    fun itHandlesInt() = runSuspendTest {
        val payload = "<node>${Int.MAX_VALUE}</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Integer))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeInt()
        val expected = 2147483647
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesByteAsNumber() = runSuspendTest {
        val payload = "<node>1</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Byte))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeByte()
        val expected: Byte = 1
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesShort() = runSuspendTest {
        val payload = "<node>${Short.MAX_VALUE}</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Short))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeShort()
        val expected: Short = 32767
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesLong() = runSuspendTest {
        val payload = "<node>${Long.MAX_VALUE}</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Struct))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeLong()
        val expected = 9223372036854775807L
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesBool() = runSuspendTest {
        val payload = "<node>true</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Boolean))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeBool()!!
        assertTrue(actual)
    }

    @Test
    fun itFailsInvalidTypeSpecificationForInt() = runSuspendTest {
        val payload = "<node>1.2</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Integer))
        }
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeStruct(objSerializer).deserializeInt()
        }
    }

    @Test
    // TODO: It's unclear if this test should result in an exception or null value.
    fun itFailsMissingTypeSpecificationForInt() = runSuspendTest {
        val payload = "<node></node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Integer))
        }
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeStruct(objSerializer).deserializeInt()
        }
    }

    @Test
    // TODO: It's unclear if this test should result in an exception or null value.
    fun itFailsWhitespaceTypeSpecificationForInt() = runSuspendTest {
        val payload = "<node> </node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Integer))
        }
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeStruct(objSerializer).deserializeInt()
        }
    }
}
