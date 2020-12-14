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
    fun `can serialize class with class field`() {
        val a = A(
            B(2)
        )
        val xml = XmlSerializer()
        a.serialize(xml)
        assertEquals("""<a><b><v>2</v></b></a>""", xml.toByteArray().decodeToString())
    }

    class A(private val b: B) : SdkSerializable {
        companion object {
            val descriptorB: SdkFieldDescriptor = SdkFieldDescriptor("b", SerialKind.Struct)

            val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
                serialName = "a"
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
            val descriptorValue = SdkFieldDescriptor("v", SerialKind.Integer)

            val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
                serialName = "b"
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
    fun `can serialize list of classes`() {
        val obj = listOf(
            B(1),
            B(2),
            B(3)
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor("list", SerialKind.List)) {
            for (value in obj) {
                value.serialize(xml)
            }
        }
        assertEquals("""<list><b><v>1</v></b><b><v>2</v></b><b><v>3</v></b></list>""", xml.toByteArray().decodeToString())
    }

    // See https://awslabs.github.io/smithy/spec/xml.html#wrapped-map-serialization
    @Test
    fun `can serialize map`() {
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
    fun `can serialize flattened map`() {
        val bar = Bar(
            mapOf(
                "example-key1" to "example1",
                "example-key2" to "example2",
                "example-key3" to "example3"
            )
        )
        val xml = XmlSerializer()
        bar.serialize(xml)

        assertEquals("""<Bar><flatMap><key>example-key1</key><value>example1</value></flatMap><flatMap><key>example-key2</key><value>example2</value></flatMap><flatMap><key>example-key3</key><value>example3</value></flatMap></Bar>""", xml.toByteArray().decodeToString())
    }

    @Test
    fun `can serialize map of lists`() {
        val objs = mapOf(
            "A1" to listOf("a", "b", "c"),
            "A2" to listOf("d", "e", "f"),
            "A3" to listOf("g", "h", "i")
        )
        val xml = XmlSerializer()
        xml.serializeMap(SdkFieldDescriptor("objs", SerialKind.Map, 0, XmlMap())) {
            for (obj in objs) {
                listEntry(obj.key, SdkFieldDescriptor("elements", SerialKind.List, 0, XmlList())) {
                    for (v in obj.value) {
                        serializeString(v)
                    }
                }
            }
        }
        assertEquals("""<objs><entry><key>A1</key><value><elements><element>a</element><element>b</element><element>c</element></elements></value></entry><entry><key>A2</key><value><elements><element>d</element><element>e</element><element>f</element></elements></value></entry><entry><key>A3</key><value><elements><element>g</element><element>h</element><element>i</element></elements></value></entry></objs>""", xml.toByteArray().decodeToString())
    }

    @Test
    fun `can serialize list of lists`() {
        val objs = listOf(
            listOf("a", "b", "c"),
            listOf("d", "e", "f"),
            listOf("g", "h", "i")
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor("objs", SerialKind.List, 0, XmlList())) {
            for (obj in objs) {
                xml.serializeList(SdkFieldDescriptor("elements", SerialKind.List, 0, XmlList())) {
                    for (v in obj) {
                        serializeString(v)
                    }
                }
            }
        }
        assertEquals("""<objs><elements><element>a</element><element>b</element><element>c</element></elements><elements><element>d</element><element>e</element><element>f</element></elements><elements><element>g</element><element>h</element><element>i</element></elements></objs>""", xml.toByteArray().decodeToString())
    }

    @Test
    fun `can serialize list of maps`() {
        val objs = listOf(
            mapOf("a" to "b", "c" to "d"),
            mapOf("e" to "f", "g" to "h"),
            mapOf("i" to "j", "k" to "l"),
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor("elements", SerialKind.List, 0, XmlList())) {
            for (obj in objs) {
                xml.serializeMap(SdkFieldDescriptor("entries", SerialKind.Map, 0, XmlMap())) {
                    for (v in obj) {
                        entry(v.key, v.value)
                    }
                }
            }
        }
        assertEquals("""<elements><entries><entry><key>a</key><value>b</value></entry><entry><key>c</key><value>d</value></entry></entries><entries><entry><key>e</key><value>f</value></entry><entry><key>g</key><value>h</value></entry></entries><entries><entry><key>i</key><value>j</value></entry><entry><key>k</key><value>l</value></entry></entries></elements>""", xml.toByteArray().decodeToString())
    }

    @Test
    fun `can serialize map of maps`() {
        val objs = mapOf(
            "A1" to mapOf("a" to "b", "c" to "d"),
            "A2" to mapOf("e" to "f", "g" to "h"),
            "A3" to mapOf("i" to "j", "k" to "l"),
        )
        val json = XmlSerializer()
        json.serializeMap(SdkFieldDescriptor("objs", SerialKind.Map, 0, XmlMap())) {
            for (obj in objs) {
                mapEntry(obj.key, SdkFieldDescriptor("objvals", SerialKind.Map, 0, XmlMap())) {
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
            val FLAT_MAP_DESCRIPTOR = SdkFieldDescriptor("flatMap", SerialKind.Map, 0, XmlMap(entry = "flatMap", flattened = true))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "Bar"
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
            val FLAT_MAP_DESCRIPTOR = SdkFieldDescriptor("values", SerialKind.Map, 0, XmlMap(entry = "entry", flattened = false))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "Foo"
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
    fun `can serialize all primitives`() {
        val xml = XmlSerializer()
        val data = Primitives(
            true, 10, 20, 30, 40, 50f, 60.0, 'A', "Str0",
            listOf(1, 2, 3)
        )
        data.serialize(xml)

        assertEquals("""<struct><boolean>true</boolean><byte>10</byte><short>20</short><int>30</int><long>40</long><float>50.0</float><double>60.0</double><char>A</char><string>Str0</string><listInt><number>1</number><number>2</number><number>3</number></listInt></struct>""", xml.toByteArray().decodeToString())
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
        val descriptorBoolean = SdkFieldDescriptor("boolean", SerialKind.Boolean)
        val descriptorByte = SdkFieldDescriptor("byte", SerialKind.Byte)
        val descriptorShort = SdkFieldDescriptor("short", SerialKind.Short)
        val descriptorInt = SdkFieldDescriptor("int", SerialKind.Integer)
        val descriptorLong = SdkFieldDescriptor("long", SerialKind.Long)
        val descriptorFloat = SdkFieldDescriptor("float", SerialKind.Float)
        val descriptorDouble = SdkFieldDescriptor("double", SerialKind.Double)
        val descriptorChar = SdkFieldDescriptor("char", SerialKind.Char)
        val descriptorString = SdkFieldDescriptor("string", SerialKind.String)
        // val descriptorUnitNullable = SdkFieldDescriptor("unitNullable")
        val descriptorListInt = SdkFieldDescriptor("listInt", SerialKind.List, 0, XmlList(elementName = "number"))
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(SdkFieldDescriptor("struct", SerialKind.Struct)) {
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
