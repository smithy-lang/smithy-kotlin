/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.serde.xml

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import software.aws.clientrt.serde.DeserializationException
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.SdkObjectDescriptor
import software.aws.clientrt.serde.SerialKind

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerPrimitiveTest {
    @Test
    fun `it handles doubles`() {
        val payload = "<node>1.2</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Double))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeDouble()
        val expected = 1.2
        assertTrue(abs(actual - expected) <= 0.0001)
    }

    @Test
    fun `it handles floats`() {
        val payload = "<node>1.2</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Float))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeFloat()
        val expected = 1.2f
        assertTrue(abs(actual - expected) <= 0.0001f)
    }

    @Test
    fun `it handles int`() {
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
    fun `it handles byte as number`() {
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
    fun `it handles short`() {
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
    fun `it handles long`() {
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
    fun `it handles bool`() {
        val payload = "<node>true</node>".encodeToByteArray()
        val deserializer = XmlDeserializer(payload)
        val objSerializer = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor("node", SerialKind.Boolean))
        }
        val actual = deserializer.deserializeStruct(objSerializer).deserializeBool()
        assertTrue(actual)
    }

    @Test
    fun `it fails invalid type specification for int`() {
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
    fun `it fails missing type specification for int`() {
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
    fun `it fails whitespace type specification for int`() {
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
