/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class XmlSerializerTest {

    @Test
    fun canSerializeClassWithClassField() {
        val a = A(
            B(2)
        )
        val xml = XmlSerializer()
        a.serialize(xml)
        assertEquals("""<a><b><v>2</v></b></a>""", xml.toByteArray().decodeToString())
    }

    class A(private val b: B) : SdkSerializable {
        companion object {
            val descriptorB: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("b"))

            val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
                trait(XmlSerialName("a"))
                field(descriptorB)
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(objectDescriptor) {
                field(descriptorB, b)
            }
        }
    }

    data class B(private val value: Int) : SdkSerializable {
        companion object {
            val descriptorValue = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("v"))

            val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
                trait(XmlSerialName("b"))
                field(descriptorValue)
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(objectDescriptor) {
                field(descriptorValue, value)
            }
        }
    }

    @Test
    fun canSerializeListOfClasses() {
        val obj = listOf(
            B(1),
            B(2),
            B(3)
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlListSetProperties("b"))) {
            for (value in obj) {
                value.serialize(xml)
            }
        }
        assertEquals("""<list><b><v>1</v></b><b><v>2</v></b><b><v>3</v></b></list>""", xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeFlatListOfClasses() {
        val obj = listOf(
            B(1),
            B(2),
            B(3)
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlListSetProperties("b"), Flattened)) {
            for (value in obj) {
                value.serialize(xml)
            }
        }
        assertEquals("""<b><v>1</v></b><b><v>2</v></b><b><v>3</v></b>""", xml.toByteArray().decodeToString())
    }

    // See https://awslabs.github.io/smithy/spec/xml.html#wrapped-map-serialization
    @Test
    fun canSerializeMap() {
        val foo = Foo(
            mapOf(
                "example-key1" to "example1",
                "example-key2" to "example2"
            )
        )
        val xml = XmlSerializer()
        foo.serialize(xml)

        assertEquals("""<Foo><values><entry><key>example-key1</key><value>example1</value></entry><entry><key>example-key2</key><value>example2</value></entry></values></Foo>""", xml.toByteArray().decodeToString())
    }

    // See https://awslabs.github.io/smithy/spec/xml.html#flattened-map-serialization
    @Test
    fun canSerializeFlattenedMap() {
        val bar = Bar(
            mapOf(
                "example-key1" to "example1",
                "example-key2" to "example2",
                "example-key3" to "example3"
            )
        )
        val xml = XmlSerializer()
        bar.serialize(xml)
        val expected = """<Bar><key>example-key1</key><value>example1</value><key>example-key2</key><value>example2</value><key>example-key3</key><value>example3</value></Bar>"""

        assertEquals(expected, xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeMapOfLists() {
        val objs = mapOf(
            "A1" to listOf("a", "b", "c"),
            "A2" to listOf("d", "e", "f"),
            "A3" to listOf("g", "h", "i")
        )
        val xml = XmlSerializer()
        xml.serializeMap(SdkFieldDescriptor(SerialKind.Map, XmlSerialName("objs"))) {
            for (obj in objs) {
                listEntry(obj.key, SdkFieldDescriptor(SerialKind.List, XmlSerialName("elements"))) {
                    for (v in obj.value) {
                        serializeString(v)
                    }
                }
            }
        }
        assertEquals("""<objs><entry><key>A1</key><value><elements><member>a</member><member>b</member><member>c</member></elements></value></entry><entry><key>A2</key><value><elements><member>d</member><member>e</member><member>f</member></elements></value></entry><entry><key>A3</key><value><elements><member>g</member><member>h</member><member>i</member></elements></value></entry></objs>""", xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeListOfLists() {
        val objs = listOf(
            listOf("a", "b", "c"),
            listOf("d", "e", "f"),
            listOf("g", "h", "i")
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor(SerialKind.List, XmlSerialName("objs"))) {
            for (obj in objs) {
                xml.serializeList(SdkFieldDescriptor(SerialKind.List, XmlSerialName("elements"))) {
                    for (v in obj) {
                        serializeString(v)
                    }
                }
            }
        }
        assertEquals("""<objs><elements><member>a</member><member>b</member><member>c</member></elements><elements><member>d</member><member>e</member><member>f</member></elements><elements><member>g</member><member>h</member><member>i</member></elements></objs>""", xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeListOfMaps() {
        val objs = listOf(
            mapOf("a" to "b", "c" to "d"),
            mapOf("e" to "f", "g" to "h"),
            mapOf("i" to "j", "k" to "l"),
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor(SerialKind.List, XmlSerialName("elements"))) {
            for (obj in objs) {
                xml.serializeMap(SdkFieldDescriptor(SerialKind.Map, XmlSerialName("entries"))) {
                    for (v in obj) {
                        entry(v.key, v.value)
                    }
                }
            }
        }
        assertEquals("""<elements><entries><entry><key>a</key><value>b</value></entry><entry><key>c</key><value>d</value></entry></entries><entries><entry><key>e</key><value>f</value></entry><entry><key>g</key><value>h</value></entry></entries><entries><entry><key>i</key><value>j</value></entry><entry><key>k</key><value>l</value></entry></entries></elements>""", xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeMapOfMaps() {
        val objs = mapOf(
            "A1" to mapOf("a" to "b", "c" to "d"),
            "A2" to mapOf("e" to "f", "g" to "h"),
            "A3" to mapOf("i" to "j", "k" to "l"),
        )
        val json = XmlSerializer()
        json.serializeMap(SdkFieldDescriptor(SerialKind.Map, XmlSerialName("objs"))) {
            for (obj in objs) {
                mapEntry(obj.key, SdkFieldDescriptor(SerialKind.Map, XmlSerialName("objvals"))) {
                    for (v in obj.value) {
                        entry(v.key, v.value)
                    }
                }
            }
        }
        assertEquals("""<objs><entry><key>A1</key><value><objvals><entry><key>a</key><value>b</value></entry><entry><key>c</key><value>d</value></entry></objvals></value></entry><entry><key>A2</key><value><objvals><entry><key>e</key><value>f</value></entry><entry><key>g</key><value>h</value></entry></objvals></value></entry><entry><key>A3</key><value><objvals><entry><key>i</key><value>j</value></entry><entry><key>k</key><value>l</value></entry></objvals></value></entry></objs>""", json.toByteArray().decodeToString())
    }

    class Bar(var flatMap: Map<String, String>? = null) : SdkSerializable {
        companion object {
            // Setting the map to be flattened removes two levels of nesting
            //                                                      *- ignored                                *- ignored
            val FLAT_MAP_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("flatMap"), XmlMapProperties(entry = "flatMap"), Flattened)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("Bar"))
                field(FLAT_MAP_DESCRIPTOR)
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                mapField(FLAT_MAP_DESCRIPTOR) {
                    for (value in flatMap!!) {
                        entry(value.key, value.value)
                    }
                }
            }
        }
    }

    class Foo(var values: Map<String, String>? = null) : SdkSerializable {
        companion object {
            val FLAT_MAP_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("values"), XmlMapProperties(entry = "entry"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("Foo"))
                field(FLAT_MAP_DESCRIPTOR)
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                mapField(FLAT_MAP_DESCRIPTOR) {
                    for (value in values!!) {
                        entry(value.key, value.value)
                    }
                }
            }
        }
    }

    @Test
    fun canSerializeAllPrimitives() {
        val xml = XmlSerializer()
        val data = Primitives(
            true, 10, 20, 30, 40, 50f, 60.0, 'A', "Str0",
            listOf(1, 2, 3)
        )
        data.serialize(xml)

        assertEquals("""<struct><boolean>true</boolean><byte>10</byte><short>20</short><int>30</int><long>40</long><float>50.0</float><double>60.0</double><char>A</char><string>Str0</string><listInt><number>1</number><number>2</number><number>3</number></listInt></struct>""", xml.toByteArray().decodeToString())
    }

    // See https://awslabs.github.io/smithy/spec/xml.html#xmlnamespace-trait
    @Test
    fun canSerializeNamespaces() {
        val myStructure = MyStructure1("example", "example")
        val xml = XmlSerializer()
        myStructure.serialize(xml)
        assertEquals("""<MyStructure xmlns="http://foo.com"><foo>example</foo><bar>example</bar></MyStructure>""", xml.toByteArray().decodeToString())

        val myStructure2 = MyStructure2("example", "example")
        val xml2 = XmlSerializer()
        myStructure2.serialize(xml2)
        assertEquals("""<MyStructure xmlns:baz="http://foo.com"><foo>example</foo><baz:bar>example</baz:bar></MyStructure>""", xml2.toByteArray().decodeToString())
    }

    class MyStructure1(private val foo: String, private val bar: String) : SdkSerializable {
        companion object {
            val fooDescriptor: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("foo"))
            val barDescriptor: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("bar"))

            val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
                trait(XmlSerialName("MyStructure"))
                trait(XmlNamespace("http://foo.com"))
                field(fooDescriptor)
                field(barDescriptor)
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(objectDescriptor) {
                field(fooDescriptor, foo)
                field(barDescriptor, bar)
            }
        }
    }

    class MyStructure2(private val foo: String, private val bar: String) : SdkSerializable {
        companion object {
            val fooDescriptor: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("foo"))
            val barDescriptor: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("baz:bar"))

            val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
                trait(XmlSerialName("MyStructure"))
                trait(XmlNamespace("http://foo.com", "baz"))

                field(fooDescriptor)
                field(barDescriptor)
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(objectDescriptor) {
                field(fooDescriptor, foo)
                field(barDescriptor, bar)
            }
        }
    }
}

data class Primitives(
    // val unit: Unit,
    val boolean: Boolean,
    val byte: Byte,
    val short: Short,
    val int: Int,
    val long: Long,
    val float: Float,
    val double: Double,
    val char: Char,
    val string: String,
    // val unitNullable: Unit?,
    val listInt: List<Int>
) : SdkSerializable {
    companion object {
        val descriptorBoolean = SdkFieldDescriptor(SerialKind.Boolean, XmlSerialName("boolean"))
        val descriptorByte = SdkFieldDescriptor(SerialKind.Byte, XmlSerialName("byte"))
        val descriptorShort = SdkFieldDescriptor(SerialKind.Short, XmlSerialName("short"))
        val descriptorInt = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("int"))
        val descriptorLong = SdkFieldDescriptor(SerialKind.Long, XmlSerialName("long"))
        val descriptorFloat = SdkFieldDescriptor(SerialKind.Float, XmlSerialName("float"))
        val descriptorDouble = SdkFieldDescriptor(SerialKind.Double, XmlSerialName("double"))
        val descriptorChar = SdkFieldDescriptor(SerialKind.Char, XmlSerialName("char"))
        val descriptorString = SdkFieldDescriptor(SerialKind.String, XmlSerialName("string"))
        // val descriptorUnitNullable = SdkFieldDescriptor("unitNullable")
        val descriptorListInt = SdkFieldDescriptor(SerialKind.List, XmlSerialName("listInt"), XmlListSetProperties(elementName = "number"))
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("struct"))) {
            serializeNull()
            field(descriptorBoolean, boolean)
            field(descriptorByte, byte)
            field(descriptorShort, short)
            field(descriptorInt, int)
            field(descriptorLong, long)
            field(descriptorFloat, float)
            field(descriptorDouble, double)
            field(descriptorChar, char)
            field(descriptorString, string)
            // serializeNull(descriptorUnitNullable)
            listField(descriptorListInt) {
                for (value in listInt) {
                    serializeInt(value)
                }
            }
        }
    }
}
