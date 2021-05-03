/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
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
        val deserializer = XmlPrimitiveDeserializer("<node>1.2</node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Double, XmlSerialName("node")))
        val actual = deserializer.deserializeDouble()
        val expected = 1.2
        assertTrue(abs(actual - expected) <= 0.0001)
    }

    @Test
    fun itHandlesFloats() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node>1.2</node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Float, XmlSerialName("node")))
        val actual = deserializer.deserializeFloat()
        val expected = 1.2f
        assertTrue(abs(actual - expected) <= 0.0001f)
    }

    @Test
    fun itHandlesInt() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node>${Int.MAX_VALUE}</node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("node")))
        val actual = deserializer.deserializeInt()
        val expected = 2147483647
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesByteAsNumber() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node>1</node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Byte, XmlSerialName("node")))
        val actual = deserializer.deserializeByte()
        val expected: Byte = 1
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesShort() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node>${Short.MAX_VALUE}</node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Short, XmlSerialName("node")))
        val actual = deserializer.deserializeShort()
        val expected: Short = 32767
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesLong() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node>${Long.MAX_VALUE}</node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Long, XmlSerialName("node")))
        val actual = deserializer.deserializeLong()
        val expected = 9223372036854775807L
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesBool() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node>true</node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Boolean, XmlSerialName("node")))
        val actual = deserializer.deserializeBoolean()
        assertTrue(actual)
    }

    @Test
    fun itFailsInvalidTypeSpecificationForInt() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node>1.2</node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("node")))
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeInt()
        }
    }

    @Test
    // TODO: It's unclear if this test should result in an exception or null value.
    fun itFailsMissingTypeSpecificationForInt() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node></node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("node")))
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeInt()
        }
    }

    @Test
    // TODO: It's unclear if this test should result in an exception or null value.
    fun itFailsWhitespaceTypeSpecificationForInt() = runSuspendTest {
        val deserializer = XmlPrimitiveDeserializer("<node> </node>".wrapInStruct(), SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("node")))
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeInt()
        }
    }

    private fun String.wrapInStruct(): ByteArray = "<structure>$this</structure>".encodeToByteArray()
}
