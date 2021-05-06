/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals

// See https://awslabs.github.io/smithy/spec/xml.html#xmlname-trait
@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerNamespaceTest {

    @Test
    fun `it handles struct with namespace declarations but default tags`() = runSuspendTest {
        val payload = """
           <MyStructure xmlns="http://foo.com">
                <foo xmlns:bar="http://foo2.com">example1</foo>
                <bar>example2</bar>
            </MyStructure>
        """.trimIndent().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = NamespaceStructTest.deserialize(deserializer)

        assertEquals("example1", bst.foo)
        assertEquals("example2", bst.bar)
    }

    class NamespaceStructTest {
        var foo: String? = null
        var bar: String? = null

        companion object {
            val FOO_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("foo"))
            val BAR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("bar"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("MyStructure"))
                trait(XmlNamespace("http://foo.com"))
                field(FOO_DESCRIPTOR)
                field(BAR_DESCRIPTOR)
            }

            suspend fun deserialize(deserializer: Deserializer): NamespaceStructTest {
                val result = NamespaceStructTest()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            FOO_DESCRIPTOR.index -> result.foo = deserializeString()
                            BAR_DESCRIPTOR.index -> result.bar = deserializeString()
                            null -> break@loop
                            else -> throw DeserializationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                        }
                    }
                }
                return result
            }
        }
    }

    @Test
    fun `it handles struct with node namespace`() = runSuspendTest {
        val payload = """
           <MyStructure xmlns:baz="http://foo.com">
                <foo>example1</foo>
                <baz:bar>example2</baz:bar>
            </MyStructure>
        """.trimIndent().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = NodeNamespaceStructTest.deserialize(deserializer)

        assertEquals("example1", bst.foo)
        assertEquals("example2", bst.bar)
    }

    class NodeNamespaceStructTest {
        var foo: String? = null
        var bar: String? = null

        companion object {
            val FOO_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("foo"))
            val BAR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("baz:bar"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("MyStructure"))
                trait(XmlNamespace("http://foo.com", "baz"))
                field(FOO_DESCRIPTOR)
                field(BAR_DESCRIPTOR)
            }

            suspend fun deserialize(deserializer: Deserializer): NodeNamespaceStructTest {
                val result = NodeNamespaceStructTest()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            FOO_DESCRIPTOR.index -> result.foo = deserializeString()
                            BAR_DESCRIPTOR.index -> result.bar = deserializeString()
                            null -> break@loop
                            else -> throw DeserializationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                        }
                    }
                }
                return result
            }
        }
    }
}
