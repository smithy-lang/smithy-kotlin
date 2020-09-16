/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import software.aws.clientrt.serde.*

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerStructTest {

    @Test
    fun `it handles basic structs with attribs`() {
        val payload = """
            <payload>
                <x value="1" />
                <y value="2" />
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = StructWithAttribsClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
    }

    @Test
    fun `it handles basic structs with attribs and text`() {
        val payload = """
            <payload>
                <x value="1">x1</x>
                <y value="2" />
                <z>true</z>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = BasicAttribTextStructTest.deserialize(deserializer)

        assertEquals(1, bst.xa)
        assertEquals("x1", bst.xt)
        assertEquals(2, bst.y)
        assertEquals(0, bst.unknownFieldCount)
    }

    class BasicAttribTextStructTest {
        var xa: Int? = null
        var xt: String? = null
        var y: Int? = null
        var z: Boolean? = null
        var unknownFieldCount: Int = 0

        companion object {
            val X_ATTRIB_DESCRIPTOR = SdkFieldDescriptor("x", SerialKind.Integer, 0, XmlAttribute("value"))
            val X_VALUE_DESCRIPTOR = SdkFieldDescriptor("x", SerialKind.Integer, 0)
            val Y_DESCRIPTOR = SdkFieldDescriptor("y", SerialKind.Integer, 0, XmlAttribute("value"))
            val Z_DESCRIPTOR = SdkFieldDescriptor("z", SerialKind.Boolean)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "payload"
                field(X_ATTRIB_DESCRIPTOR)
                field(X_VALUE_DESCRIPTOR)
                field(Y_DESCRIPTOR)
                field(Z_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): BasicAttribTextStructTest {
                val result = BasicAttribTextStructTest()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            X_ATTRIB_DESCRIPTOR.index -> result.xa = deserializeInt()
                            X_VALUE_DESCRIPTOR.index -> result.xt = deserializeString()
                            Y_DESCRIPTOR.index -> result.y = deserializeInt()
                            Z_DESCRIPTOR.index -> result.z = deserializeBool()
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                                result.unknownFieldCount++
                                skipValue()
                            }
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                        }
                    }
                }
                return result
            }
        }
    }

    @Test
    fun `it handles basic structs`() {
        val payload = """
            <payload>
                <x>1</x>
                <y>2</y>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = SimpleStructClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
    }

    @Test
    fun `it handles basic structs with null values`() {
        val payload1 = """
            <payload>
                <x>1</x>
                <y></y>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload1)
        val bst = SimpleStructClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(null, bst.y)

        val payload2 = """
            <payload>
                <x></x>
                <y>2</y>
            </payload>
        """.encodeToByteArray()

        val deserializer2 = XmlDeserializer(payload2)
        val bst2 = SimpleStructClass.deserialize(deserializer2)

        assertEquals(null, bst2.x)
        assertEquals(2, bst2.y)
    }

    @Test
    fun `it enumerates unknown struct fields`() {
        val payload = """
               <payload>
                   <x>1</x>
                   <z>unknown field</z>
                   <y>2</y>
               </payload>
           """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = SimpleStructClass.deserialize(deserializer)

        assertTrue(bst.unknownFieldCount == 1, "unknown field not enumerated")
    }
}
