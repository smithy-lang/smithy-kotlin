/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals

/*
Remove all whitespace and newline chars from XML string and return the compact formo
e.g.

```
<Root>
    <nested>
        <bar>1</bar>
    </nested>
</Root>
```

becomes: `<Root><nested><bar>1</bar></nested></Root>`
 */
private fun String.toXmlCompactString(): String =
    trimIndent()
        .replace("\n", "")
        .replace(Regex(">\\s+"), ">")

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
    fun canSerializePrimitiveList() {
        // https://awslabs.github.io/smithy/spec/xml.html#wrapped-list-serialization
        val list = listOf("example1", "example2", "example3")
        val xml = XmlSerializer()
        val listDescriptor = SdkFieldDescriptor(SerialKind.List, XmlSerialName("values"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("Foo"))
            field(listDescriptor)
        }

        xml.serializeStruct(objDescriptor) {
            listField(listDescriptor) {
                for (value in list) {
                    serializeString(value)
                }
            }
        }

        val expected = """
            <Foo>
                <values>
                    <member>example1</member>
                    <member>example2</member>
                    <member>example3</member>
                </values>               
            </Foo>
            """.toXmlCompactString()
        assertEquals(expected, xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeRenamedList() {
        val list = listOf("example1", "example2", "example3")
        val xml = XmlSerializer()
        val listDescriptor = SdkFieldDescriptor(SerialKind.List, XmlSerialName("values"), XmlCollectionName("Item"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("Foo"))
            field(listDescriptor)
        }

        xml.serializeStruct(objDescriptor) {
            listField(listDescriptor) {
                for (value in list) {
                    serializeString(value)
                }
            }
        }

        val expected = """
            <Foo>
                <values>
                    <Item>example1</Item>
                    <Item>example2</Item>
                    <Item>example3</Item>
                </values>               
            </Foo>
            """.toXmlCompactString()
        assertEquals(expected, xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeFlattenedList() {
        // https://awslabs.github.io/smithy/spec/xml.html#flattened-list-serialization
        val list = listOf("example1", "example2", "example3")
        val xml = XmlSerializer()
        val listDescriptor = SdkFieldDescriptor(SerialKind.List, XmlSerialName("flat"), Flattened)
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("Foo"))
            field(listDescriptor)
        }

        xml.serializeStruct(objDescriptor) {
            listField(listDescriptor) {
                for (value in list) {
                    serializeString(value)
                }
            }
        }

        val expected = """
            <Foo>
                <flat>example1</flat>
                <flat>example2</flat>
                <flat>example3</flat>
            </Foo>
            """.toXmlCompactString()
        assertEquals(expected, xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeListOfClasses() {
        val obj = listOf(
            B(1),
            B(2),
            B(3)
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"))) {
            for (value in obj) {
                serializeSdkSerializable(value)
            }
        }

        val expected = """
            <list>
                <member>
                    <v>1</v>
                </member>
                <member>
                    <v>2</v>
                </member>
                <member>
                    <v>3</v>
                </member>
            </list>               
            """.toXmlCompactString()
        assertEquals(expected, xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeFlatListOfClasses() {
        val obj = listOf(
            B(1),
            B(2),
            B(3)
        )
        val xml = XmlSerializer()
        xml.serializeList(SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), Flattened)) {
            for (value in obj) {
                serializeSdkSerializable(value)
            }
        }
        val expected = """
            <list>
                <v>1</v>
            </list>
            <list>
                <v>2</v>
            </list>
            <list>
                <v>3</v>
            </list>
        """.toXmlCompactString()
        assertEquals(expected, xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeMap() {
        // See https://awslabs.github.io/smithy/spec/xml.html#wrapped-map-serialization
        val foo = Foo(
            mapOf(
                "example-key1" to "example1",
                "example-key2" to "example2"
            )
        )
        val xml = XmlSerializer()
        foo.serialize(xml)

        val expected = """
            <Foo>
                <values>
                    <entry>
                        <key>example-key1</key>
                        <value>example1</value>
                    </entry>
                    <entry>
                        <key>example-key2</key>
                        <value>example2</value>
                    </entry>
                </values>
            </Foo>
        """.toXmlCompactString()

        assertEquals(expected, xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeFlattenedMap() {
        // See https://awslabs.github.io/smithy/spec/xml.html#flattened-map-serialization
        val bar = Bar(
            mapOf(
                "example-key1" to "example1",
                "example-key2" to "example2",
                "example-key3" to "example3"
            )
        )
        val serializer = XmlSerializer()
        bar.serialize(serializer)

        val expected = """
        <Bar>
            <flatMap>
                <key>example-key1</key>
                <value>example1</value>
            </flatMap>
            
            <flatMap>
                <key>example-key2</key>
                <value>example2</value>
            </flatMap>
            
            <flatMap>
                <key>example-key3</key>
                <value>example3</value>
            </flatMap>
        </Bar>
        """.toXmlCompactString()

        assertEquals(expected, serializer.toByteArray().decodeToString())
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

        val expected = """
            <objs>
                <entry>
                    <key>A1</key>
                    <value>
                        <elements>
                            <member>a</member>
                            <member>b</member>
                            <member>c</member>
                        </elements>
                    </value>
                </entry>
                <entry>
                    <key>A2</key>
                    <value>
                        <elements>
                            <member>d</member>
                            <member>e</member>
                            <member>f</member>
                        </elements>
                    </value>
                </entry>
                <entry>
                    <key>A3</key>
                    <value>
                        <elements>
                            <member>g</member>
                            <member>h</member>
                            <member>i</member>
                        </elements>
                    </value>
                </entry>
            </objs>
        """.toXmlCompactString()
        assertEquals(expected, xml.toByteArray().decodeToString())
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

        val expected = """
            <objs>
                <elements>
                    <member>a</member>
                    <member>b</member>
                    <member>c</member>
                </elements>
                <elements>
                    <member>d</member>
                    <member>e</member>
                    <member>f</member>
                </elements>
                <elements>
                    <member>g</member>
                    <member>h</member>
                    <member>i</member>
                </elements>
            </objs>
        """.toXmlCompactString()
        assertEquals(expected, xml.toByteArray().decodeToString())
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
        val expected = """
            <elements>
                <entries>
                    <entry>
                        <key>a</key>
                        <value>b</value>
                    </entry>
                    <entry>
                        <key>c</key>
                        <value>d</value>
                    </entry>
                </entries>
                <entries>
                    <entry>
                        <key>e</key>
                        <value>f</value>
                    </entry>
                    <entry>
                        <key>g</key>
                        <value>h</value>
                    </entry>
                </entries>
                <entries>
                    <entry>
                        <key>i</key>
                        <value>j</value>
                    </entry>
                    <entry>
                        <key>k</key>
                        <value>l</value>
                    </entry>
                </entries>
            </elements>
        """.toXmlCompactString()
        assertEquals(expected, xml.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeMapOfMaps() {
        val objs = mapOf(
            "A1" to mapOf("a" to "b", "c" to "d"),
            "A2" to mapOf("e" to "f", "g" to "h"),
            "A3" to mapOf("i" to "j", "k" to "l"),
        )
        val serializer = XmlSerializer()
        serializer.serializeMap(SdkFieldDescriptor(SerialKind.Map, XmlSerialName("objs"))) {
            for (obj in objs) {
                mapEntry(obj.key, SdkFieldDescriptor(SerialKind.Map, XmlSerialName("objvals"))) {
                    for (v in obj.value) {
                        entry(v.key, v.value)
                    }
                }
            }
        }
        val expected = """
            <objs>
                <entry>
                    <key>A1</key>
                    <value>
                        <objvals>
                            <entry>
                                <key>a</key>
                                <value>b</value>
                            </entry>
                            <entry>
                                <key>c</key>
                                <value>d</value>
                            </entry>
                        </objvals>
                    </value>
                </entry>
                <entry>
                    <key>A2</key>
                    <value>
                        <objvals>
                            <entry>
                                <key>e</key>
                                <value>f</value>
                            </entry>
                            <entry>
                                <key>g</key>
                                <value>h</value>
                            </entry>
                        </objvals>
                    </value>
                </entry>
                <entry>
                    <key>A3</key>
                    <value>
                        <objvals>
                            <entry>
                                <key>i</key>
                                <value>j</value>
                            </entry>
                            <entry>
                                <key>k</key>
                                <value>l</value>
                            </entry>
                        </objvals>
                    </value>
                </entry>
            </objs>
            """.toXmlCompactString()
        assertEquals(expected, serializer.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeMapOfStructs() {
        val objs = mapOf(
            "foo" to B(1),
            "bar" to B(2),
        )

        val serializer = XmlSerializer()

        serializer.serializeMap(SdkFieldDescriptor(SerialKind.Map, XmlSerialName("myMap"))) {
            objs.entries.forEach { (key, value) -> entry(key, value) }
        }

        val expected = """
            <myMap>
                <entry>
                    <key>foo</key>
                    <value>
                        <v>1</v>
                    </value>
                </entry>
                <entry>
                    <key>bar</key>
                    <value>
                        <v>2</v>
                    </value>
                </entry>
            </myMap>
            """.toXmlCompactString()
        assertEquals(expected, serializer.toByteArray().decodeToString())
    }

    class Bar(var flatMap: Map<String, String>? = null) : SdkSerializable {
        companion object {
            val FLAT_MAP_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("flatMap"), XmlMapName(entry = "flatMap"), Flattened)
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
            val FLAT_MAP_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("values"), XmlMapName(entry = "entry"))
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

    @Test
    fun canSerializeNamespaces() {
        // See https://awslabs.github.io/smithy/spec/xml.html#xmlnamespace-trait
        val myStructure = MyStructure1("example", "example")
        val xml = XmlSerializer()
        myStructure.serialize(xml)
        val expected1 = """
            <MyStructure xmlns="http://foo.com">
                <foo>example</foo>
                <bar>example</bar>
            </MyStructure>
        """.toXmlCompactString()
        assertEquals(expected1, xml.toByteArray().decodeToString())

        val myStructure2 = MyStructure2("example", "example")
        val xml2 = XmlSerializer()
        myStructure2.serialize(xml2)
        val expected2 = """
            <MyStructure xmlns:baz="http://foo.com">
                <foo>example</foo>
                <baz:bar>example</baz:bar>
            </MyStructure>
        """.toXmlCompactString()
        assertEquals(expected2, xml2.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeNestedNamespaces() {
        val input = XmlNamespacesRequest(
            nested = XmlNamespaceNested(
                foo = "Foo",
                values = listOf("Bar", "Baz")
            )
        )

        val serializer = XmlSerializer()
        input.serialize(serializer)

        val expected = """
            <XmlNamespacesInputOutput xmlns="http://foo.com">
                <nested>
                    <foo xmlns:baz="http://baz.com">Foo</foo>
                    <values xmlns="http://qux.com">
                        <member xmlns="http://bux.com">Bar</member>
                        <member xmlns="http://bux.com">Baz</member>
                    </values>
                </nested>
            </XmlNamespacesInputOutput>
        """.toXmlCompactString()

        assertEquals(expected, serializer.toByteArray().decodeToString())
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

    @Test
    fun canIgnoresNestedStructNamespaces() {
        /*
            @xmlNamespace(uri: "http://foo.com")
            structure Foo {
                nested: Bar,
            }

            // Ignored - not at top level
            // TODO - nothing in the spec defines this...only the protocol tests
            @xmlNamespace(uri: "http://bar.com")
            structure Bar {
                x: String
            }
        */

        val serializer = XmlSerializer()
        val nestedDescriptor = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("nested"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("Foo"))
            trait(XmlNamespace("http://foo.com"))
            field(nestedDescriptor)
        }

        val nested = object : SdkSerializable {
            override fun serialize(serializer: Serializer) {
                val xDescriptor = SdkFieldDescriptor(SerialKind.String, XmlSerialName("x"))
                val obj2Descriptor = SdkObjectDescriptor.build {
                    trait(XmlSerialName("Bar"))
                    trait(XmlNamespace("http://bar.com"))
                    field(xDescriptor)
                }
                serializer.serializeStruct(obj2Descriptor) {
                    field(xDescriptor, "blerg")
                }
            }
        }

        serializer.serializeStruct(objDescriptor) {
            field(nestedDescriptor, nested)
        }

        val expected = """
            <Foo xmlns="http://foo.com">
                <nested>
                    <x>blerg</x>
                </nested>
            </Foo>
        """.toXmlCompactString()

        assertEquals(expected, serializer.toByteArray().decodeToString())
    }

    @Test
    fun itSerializesRecursiveShapes() {
        val expected = """
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
        """.toXmlCompactString()

        val input = RecursiveShapesInputOutput {
            nested = RecursiveShapesInputOutputNested1 {
                foo = "Foo1"
                nested = RecursiveShapesInputOutputNested2 {
                    bar = "Bar1"
                    recursiveMember = RecursiveShapesInputOutputNested1 {
                        foo = "Foo2"
                        nested = RecursiveShapesInputOutputNested2 {
                            bar = "Bar2"
                        }
                    }
                }
            }
        }

        val serializer = XmlSerializer()
        RecursiveShapesInputOutputSerializer().serialize(serializer, input)
        val actual = serializer.toByteArray().decodeToString()
        println(actual)
        assertEquals(expected, actual)
    }

    @Test
    fun itCanSerializeAttributes() {
        val boolDescriptor = SdkFieldDescriptor(SerialKind.Boolean, XmlSerialName("bool"), XmlAttribute)
        val strDescriptor = SdkFieldDescriptor(SerialKind.Boolean, XmlSerialName("str"), XmlAttribute)
        val intDescriptor = SdkFieldDescriptor(SerialKind.Boolean, XmlSerialName("number"), XmlAttribute)
        // timestamps are ignored as they aren't special cased (as of right now) but rather serialized through string/raw

        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("Foo"))
            field(boolDescriptor)
            field(strDescriptor)
            field(intDescriptor)
        }

        // NOTE: attribute fields MUST be generated as the first fields after serializeStruct() to work properly
        val serializer = XmlSerializer()
        serializer.serializeStruct(objDescriptor) {
            field(boolDescriptor, true)
            field(strDescriptor, "bar")
            field(intDescriptor, 2)
        }

        val expected = """
            <Foo bool="true" str="bar" number="2" />
        """.toXmlCompactString()

        assertEquals(expected, serializer.toByteArray().decodeToString())
    }

    @Test
    fun itCanSerializeAttributesWithNamespaces() {
        val nestedDescriptor = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("nestedField"), XmlNamespace("https://example.com", "xsi"))

        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("Foo"))
            field(nestedDescriptor)
        }

        val nestedSerializer = object : SdkSerializable {
            override fun serialize(serializer: Serializer) {
                val attrDescriptor = SdkFieldDescriptor(SerialKind.String, XmlSerialName("xsi:myAttr"), XmlAttribute)
                val nestedObjDescriptor = SdkObjectDescriptor.build {
                    trait(XmlSerialName("Nested"))
                    field(attrDescriptor)
                }
                serializer.serializeStruct(nestedObjDescriptor) {
                    field(attrDescriptor, "nestedAttrValue")
                }
            }
        }

        // NOTE: attribute fields MUST be generated as the first fields after serializeStruct() to work properly
        val serializer = XmlSerializer()
        serializer.serializeStruct(objDescriptor) {
            field(nestedDescriptor, nestedSerializer)
        }

        // ... the order these attributes come out w.r.t namespaces is not well defined
        val expected = """
            <Foo>
                <nestedField xsi:myAttr="nestedAttrValue" xmlns:xsi="https://example.com" />
            </Foo>
        """.toXmlCompactString()

        assertEquals(expected, serializer.toByteArray().decodeToString())
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
        val descriptorListInt = SdkFieldDescriptor(SerialKind.List, XmlSerialName("listInt"), XmlCollectionName(element = "number"))
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

// structure RecursiveShapesInputOutput {
//     nested: RecursiveShapesInputOutputNested1
// }
//
// structure RecursiveShapesInputOutputNested1 {
//     foo: String,
//     nested: RecursiveShapesInputOutputNested2
// }
//
// structure RecursiveShapesInputOutputNested2 {
//     bar: String,
//     recursiveMember: RecursiveShapesInputOutputNested1,
// }
internal class RecursiveShapesInputOutputSerializer {
    companion object {
        private val NESTED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("nested"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("RecursiveShapesInputOutput"))
            field(NESTED_DESCRIPTOR)
        }
    }

    fun serialize(serializer: Serializer, input: RecursiveShapesInputOutput) {
        serializer.serializeStruct(OBJ_DESCRIPTOR) {
            input.nested?.let { field(NESTED_DESCRIPTOR, RecursiveShapesInputOutputNested1DocumentSerializer(it)) }
        }
    }
}

internal class RecursiveShapesInputOutputNested1DocumentSerializer(val input: RecursiveShapesInputOutputNested1) : SdkSerializable {

    companion object {
        private val FOO_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("foo"))
        private val NESTED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("nested"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("RecursiveShapesInputOutputNested1"))
            field(FOO_DESCRIPTOR)
            field(NESTED_DESCRIPTOR)
        }
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(OBJ_DESCRIPTOR) {
            input.foo?.let { field(FOO_DESCRIPTOR, it) }
            input.nested?.let { field(NESTED_DESCRIPTOR, RecursiveShapesInputOutputNested2DocumentSerializer(it)) }
        }
    }
}

internal class RecursiveShapesInputOutputNested2DocumentSerializer(val input: RecursiveShapesInputOutputNested2) : SdkSerializable {

    companion object {
        private val BAR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("bar"))
        private val RECURSIVEMEMBER_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("recursiveMember"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("RecursiveShapesInputOutputNested2"))
            field(BAR_DESCRIPTOR)
            field(RECURSIVEMEMBER_DESCRIPTOR)
        }
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(OBJ_DESCRIPTOR) {
            input.bar?.let { field(BAR_DESCRIPTOR, it) }
            input.recursiveMember?.let { field(RECURSIVEMEMBER_DESCRIPTOR, RecursiveShapesInputOutputNested1DocumentSerializer(it)) }
        }
    }
}
