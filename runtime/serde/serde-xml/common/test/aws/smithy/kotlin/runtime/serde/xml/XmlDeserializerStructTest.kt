/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerStructTest {
    @Test
    fun `it handles basic structs with attribs`() {
        val payload = """
               <?xml version="1.0" encoding="UTF-8"?>
               <!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0
                 -->
                
               <payload x="1" y="2" />
        """.trimIndent().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = StructWithAttribsClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
    }

    @Test
    fun `it handles basic structs with multi attribs and text`() {
        val payload = """
               <?xml version="1.0" encoding="UTF-8"?>
               <!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0
                 -->
                
               <payload xval="1" yval="2">
                    <x>nodeval</x>
               </payload>
        """.trimIndent().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = StructWithMultiAttribsAndTextValClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
        assertEquals("nodeval", bst.txt)
    }

    @Test
    fun itHandlesBasicStructsWithAttribsAndText() {
        val payload = """
            <payload xa="1" ya="2">
                <x>x1</x>
                <y/>
                <z>true</z>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = BasicAttribTextStructTest.deserialize(deserializer)

        assertEquals(1, bst.xa)
        assertEquals("x1", bst.xt)
        assertEquals(2, bst.y)
        assertEquals(1, bst.unknownFieldCount)
    }

    class BasicAttribTextStructTest {
        var xa: Int? = null
        var xt: String? = null
        var y: Int? = null
        var z: Boolean? = null
        var unknownFieldCount: Int = 0

        companion object {
            val X_ATTRIB_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("xa"), XmlAttribute)
            val X_VALUE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"))
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("ya"), XmlAttribute)
            val Z_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, XmlSerialName("z"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("payload"))
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
                            Z_DESCRIPTOR.index -> result.z = deserializeBoolean()
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                                result.unknownFieldCount++
                                skipValue()
                            }
                            else -> throw DeserializationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                        }
                    }
                }
                return result
            }
        }
    }

    @Test
    fun itHandlesBasicStructs() {
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
    fun itHandlesBasicStructsWithNullValues() {
        val payload1 = """
            <payload>
                <x>a</x>
                <y></y>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload1)
        val bst = SimpleStructOfStringsClass.deserialize(deserializer)

        assertEquals("a", bst.x)
        assertEquals("", bst.y)

        val payload2 = """
            <payload>
                <x></x>
                <y>2</y>
            </payload>
        """.encodeToByteArray()

        val deserializer2 = XmlDeserializer(payload2)
        val bst2 = SimpleStructOfStringsClass.deserialize(deserializer2)

        assertEquals("", bst2.x)
        assertEquals("2", bst2.y)
    }

    @Test
    fun itEnumeratesUnknownStructFields() {
        val payload = """
               <payload z="strval">
                   <x>1</x>
                   <y>2</y>
               </payload>
           """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = SimpleStructClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
        assertEquals("strval", bst.z)
    }

    @Test
    fun itHandlesNestedXmlStructures() {
        val payload = """
            <RecursiveShapesInputOutput>
                <nested>
                    <foo>Foo1</foo>
                    <nested>
                        <bar>Bar1</bar>
                        <recursiveMember>
                            <foo>Foo2</foo>
                            <nested>
                                <bar>Bar2</bar>
                            </nested>
                        </recursiveMember>
                    </nested>
                </nested>
            </RecursiveShapesInputOutput>
           """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = RecursiveShapesOperationDeserializer().deserialize(deserializer)

        println(bst.nested?.nested)
    }

    class BasicUnwrappedTextStructureTest {
        var x: String? = null

        companion object {
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("payload"))
                trait(XmlUnwrappedOutput)
            }

            fun deserialize(deserializer: Deserializer): BasicUnwrappedTextStructureTest {
                val result = BasicUnwrappedTextStructureTest()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            OBJ_DESCRIPTOR.index -> result.x = deserializeString()
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
    fun itHandlesBasicUnwrappedStructs() {
        val payload = """
            <payload>text</payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = BasicUnwrappedTextStructureTest.deserialize(deserializer)

        assertEquals("text", bst.x)
    }

    @Test
    fun itHandlesBasicUnwrappedStructsWithNullValues() {
        val payload = """
            <payload></payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = BasicUnwrappedTextStructureTest.deserialize(deserializer)

        assertEquals(null, bst.x)
    }

    class AliasStruct {
        var message: String? = null
        var attribute: String? = null

        companion object {
            val MESSAGE_DESCRIPTOR = SdkFieldDescriptor(
                SerialKind.String,
                XmlSerialName("Message"),
                XmlAliasName("message"),
                XmlAliasName("msg"),
            )
            val ATTRIBUTE_DESCRIPTOR = SdkFieldDescriptor(
                SerialKind.String,
                XmlAttribute,
                XmlSerialName("Attribute"),
                XmlAliasName("attribute"),
                XmlAliasName("attr"),
            )
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("Struct"))
                trait(XmlAliasName("struct"))
                field(MESSAGE_DESCRIPTOR)
                field(ATTRIBUTE_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): AliasStruct {
                val result = AliasStruct()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            MESSAGE_DESCRIPTOR.index -> result.message = deserializeString()
                            ATTRIBUTE_DESCRIPTOR.index -> result.attribute = deserializeString()
                            null -> break@loop
                            else -> throw DeserializationException(IllegalStateException("unexpected field in AliasStruct deserializer"))
                        }
                    }
                }
                return result
            }
        }
    }

    @Test
    fun itHandlesAliasMatchingOnElements() {
        val tests = listOf(
            "<Struct><Message>Hi there</Message></Struct>",
            "<Struct><message>Hi there</message></Struct>",
            "<Struct><msg>Hi there</msg></Struct>",
            "<struct><Message>Hi there</Message></struct>",
        )
        tests.forEach { payload ->
            val deserializer = XmlDeserializer(payload.encodeToByteArray())
            val bst = AliasStruct.deserialize(deserializer)
            assertEquals("Hi there", bst.message, "Can't find 'Hi there' in $payload")
        }
    }

    @Test
    fun itHandlesAliasMatchingOnAttributes() {
        val tests = listOf(
            """<Struct Attribute="Hi there"></Struct>""",
            """<Struct attribute="Hi there"></Struct>""",
            """<Struct attr="Hi there"></Struct>""",
        )
        tests.forEach { payload ->
            val deserializer = XmlDeserializer(payload.encodeToByteArray())
            val bst = AliasStruct.deserialize(deserializer)
            assertEquals("Hi there", bst.attribute, "Can't find 'Hi there' in $payload")
        }
    }
}

internal class RecursiveShapesOperationDeserializer {

    companion object {
        private val NESTED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("nested"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("RecursiveShapesInputOutput"))
            field(NESTED_DESCRIPTOR)
        }
    }

    fun deserialize(deserializer: Deserializer): RecursiveShapesInputOutput {
        val builder = RecursiveShapesInputOutput.Builder()

        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    NESTED_DESCRIPTOR.index -> builder.nested = RecursiveShapesInputOutputNested1DocumentDeserializer().deserialize(deserializer)
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        return builder.build()
    }
}

internal class RecursiveShapesInputOutputNested1DocumentDeserializer {

    companion object {
        private val FOO_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("foo"))
        private val NESTED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("nested"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            field(FOO_DESCRIPTOR)
            field(NESTED_DESCRIPTOR)
        }
    }

    fun deserialize(deserializer: Deserializer): RecursiveShapesInputOutputNested1 {
        val builder = RecursiveShapesInputOutputNested1.dslBuilder()
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    FOO_DESCRIPTOR.index -> builder.foo = deserializeString()
                    NESTED_DESCRIPTOR.index -> builder.nested = RecursiveShapesInputOutputNested2DocumentDeserializer().deserialize(deserializer)
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        return builder.build()
    }
}

internal class RecursiveShapesInputOutputNested2DocumentDeserializer {

    companion object {
        private val BAR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("bar"))
        private val RECURSIVEMEMBER_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("recursiveMember"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            field(BAR_DESCRIPTOR)
            field(RECURSIVEMEMBER_DESCRIPTOR)
        }
    }

    fun deserialize(deserializer: Deserializer): RecursiveShapesInputOutputNested2 {
        val builder = RecursiveShapesInputOutputNested2.dslBuilder()
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    BAR_DESCRIPTOR.index -> builder.bar = deserializeString()
                    RECURSIVEMEMBER_DESCRIPTOR.index -> builder.recursiveMember = RecursiveShapesInputOutputNested1DocumentDeserializer().deserialize(deserializer)
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        return builder.build()
    }
}
