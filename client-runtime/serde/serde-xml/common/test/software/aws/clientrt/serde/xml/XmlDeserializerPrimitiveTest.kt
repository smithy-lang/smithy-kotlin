/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerPrimitiveTest {
    @Test
    fun itHandlesDoubles() {
        val deserializer = XmlDeserializer2("<node>1.2</node>".wrapInStruct())
        val actual = deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Double, XmlSerialName("node")), deserializer::deserializeDouble)
        val expected = 1.2
        assertTrue(abs(actual - expected) <= 0.0001)
    }

    @Test
    fun itHandlesFloats() {
        val deserializer = XmlDeserializer2("<node>1.2</node>".wrapInStruct())
        val actual = deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Float, XmlSerialName("node")), deserializer::deserializeFloat)
        val expected = 1.2f
        assertTrue(abs(actual - expected) <= 0.0001f)
    }

    @Test
    fun itHandlesInt() {
        val deserializer = XmlDeserializer2("<node>${Int.MAX_VALUE}</node>".wrapInStruct())
        val actual = deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("node")), deserializer::deserializeInt)
        val expected = 2147483647
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesByteAsNumber() {
        val deserializer = XmlDeserializer2("<node>1</node>".wrapInStruct())
        val actual = deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Byte, XmlSerialName("node")), deserializer::deserializeByte)
        val expected: Byte = 1
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesShort() {
        val deserializer = XmlDeserializer2("<node>${Short.MAX_VALUE}</node>".wrapInStruct())
        val actual = deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Short, XmlSerialName("node")), deserializer::deserializeShort)
        val expected: Short = 32767
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesLong() {
        val deserializer = XmlDeserializer2("<node>${Long.MAX_VALUE}</node>".wrapInStruct())
        val actual = deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Long, XmlSerialName("node")), deserializer::deserializeLong)
        val expected = 9223372036854775807L
        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesBool() {
        val deserializer = XmlDeserializer2("<node>true</node>".wrapInStruct())
        val actual = deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Boolean, XmlSerialName("node")), deserializer::deserializeBoolean)
        assertTrue(actual)
    }

    @Test
    fun itFailsInvalidTypeSpecificationForInt() {
        val deserializer = XmlDeserializer2("<node>1.2</node>".wrapInStruct())
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("node")), deserializer::deserializeInt)
        }
    }

    @Test
    // TODO: It's unclear if this test should result in an exception or null value.
    fun itFailsMissingTypeSpecificationForInt() {
        val deserializer = XmlDeserializer2("<node></node>".wrapInStruct())
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("node")), deserializer::deserializeInt)
        }
    }

    @Test
    // TODO: It's unclear if this test should result in an exception or null value.
    fun itFailsWhitespaceTypeSpecificationForInt() {
        val deserializer = XmlDeserializer2("<node> </node>".wrapInStruct())
        assertFailsWith(DeserializationException::class) {
            deserializer.deserializeSingleValue(SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("node")), deserializer::deserializeInt)
        }
    }

    private fun String.wrapInStruct(): ByteArray = "<structure>$this</structure>".encodeToByteArray()

    private fun <T> Deserializer.deserializeSingleValue(fieldDescriptor: SdkFieldDescriptor, deserializeFn: () -> T): T {
        val objSerializer = SdkObjectDescriptor.build {
            trait(XmlSerialName("structure"))
            field(fieldDescriptor)
        }
        var actual: T? = null
        deserializeStruct(objSerializer) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    0 -> actual = deserializeFn()
                    null -> break@loop
                    else -> throw XmlGenerationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                }
            }
        }

        return actual ?: throw DeserializationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
    }
}
