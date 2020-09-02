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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import kotlin.math.abs
import kotlin.test.*
import software.aws.clientrt.serde.*

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerTest {
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

    @Test
    fun `it handles lists`() {
        val payload = """
            <list>
                <element>1</element>
                <element>2</element>
                <element>3</element>
            </list>
        """.flatten().encodeToByteArray()
        val listWrapperFieldDescriptor = SdkFieldDescriptor("list", SerialKind.List, 0, XmlList())
        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
            val list = mutableListOf<Int>()
            while (hasNextElement()) {
                list.add(deserializeInt())
            }
            return@deserializeList list
        }
        val expected = listOf(1, 2, 3)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun `it handles maps with default node names`() {
        val payload = """
            <values>
                <entry>
                    <key>key1</key>
                    <value>1</value>
                </entry>
                <entry>
                    <key>key2</key>
                    <value>2</value>
                </entry>
            </values>
        """.flatten().encodeToByteArray()
        val fieldDescriptor = SdkFieldDescriptor("values", SerialKind.Map, 0, XmlMap())

        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeMap(fieldDescriptor) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                val key = key()
                val value = deserializeInt()

                map[key] = value
            }
            return@deserializeMap map
        }
        val expected = mapOf("key1" to 1, "key2" to 2)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun `it handles maps with custom node names`() {
        val payload = """
            <mymap>
                <myentry>
                    <mykey>key1</mykey>
                    <myvalue>1</myvalue>
                </myentry>
                <myentry>
                    <mykey>key2</mykey>
                    <myvalue>2</myvalue>
                </myentry>
            </mymap>
        """.flatten().encodeToByteArray()
        val fieldDescriptor =
            SdkFieldDescriptor("mymap", SerialKind.Map, 0, XmlMap("myentry", "mykey", "myvalue"))
        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeMap(fieldDescriptor) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                val key = key()
                val value = deserializeInt()

                map[key] = value
            }
            return@deserializeMap map
        }
        val expected = mapOf("key1" to 1, "key2" to 2)
        actual.shouldContainExactly(expected)
    }

    // https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html#flattened-map-serialization
    @Test
    fun `it handles flat maps`() {
        val payload = """
            <Bar>
                <flatMap>
                    <key>key1</key>
                    <value>1</value>
                </flatMap>
                <flatMap>
                    <key>key2</key>
                    <value>2</value>
                </flatMap>
                <flatMap>
                    <key>key3</key>
                    <value>3</value>
                </flatMap>
            </Bar>
        """.flatten().encodeToByteArray()
        val containerFieldDescriptor =
            SdkFieldDescriptor("Bar", SerialKind.Map, 0, XmlMap("flatMap", "key", "value", true))
        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeMap(containerFieldDescriptor) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializer.deserializeInt()
            }
            return@deserializeMap map
        }
        val expected = mapOf("key1" to 1, "key2" to 2, "key3" to 3)
        actual.shouldContainExactly(expected)
    }

    class BasicStructTest {
        var x: Int? = null
        var y: Int? = null
        var unknownFieldCount: Int = 0

        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor("x", SerialKind.Integer)
            val Y_DESCRIPTOR = SdkFieldDescriptor("y", SerialKind.Integer)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "payload"
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): BasicStructTest {
                val result = BasicStructTest()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            X_DESCRIPTOR.index -> result.x = deserializeInt()
                            Y_DESCRIPTOR.index -> result.y = deserializeInt()
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
    fun `it handles basic structs with attribs`() {
        val payload = """
            <payload>
                <x value="1" />
                <y value="2" />
            </payload>
        """.flatten().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = BasicAttribStructTest.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
    }

    class BasicAttribStructTest {
        var x: Int? = null
        var y: Int? = null
        var unknownFieldCount: Int = 0

        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor("x", SerialKind.Integer, 0, XmlAttribute("value"))
            val Y_DESCRIPTOR = SdkFieldDescriptor("y", SerialKind.Integer, 0, XmlAttribute("value"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "payload"
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): BasicAttribStructTest {
                val result = BasicAttribStructTest()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            X_DESCRIPTOR.index -> result.x = deserializeInt()
                            Y_DESCRIPTOR.index -> result.y = deserializeInt()
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
    fun `it handles basic structs with attribs and text`() {
        val payload = """
            <payload>
                <x value="1">x1</x>
                <y value="2" />
                <z>true</z>
            </payload>
        """.flatten().encodeToByteArray()

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
        """.flatten().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = BasicStructTest.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
    }

    @Test
    fun `it handles list of objects`() {
        val payload = """
               <list>
                   <payload>
                       <x>1</x>
                       <y>2</y>
                   </payload>
                   <payload>
                       <x>3</x>
                       <y>4</y>
                   </payload>
               </list>
           """.flatten().encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor("list", SerialKind.List, 0, XmlList(elementName = "payload"))

        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
            val list = mutableListOf<BasicStructTest>()
            while (hasNextElement()) {
                val obj = BasicStructTest.deserialize(deserializer)
                list.add(obj)
            }
            return@deserializeList list
        }
        assertEquals(2, actual.size)
        assertEquals(1, actual[0].x)
        assertEquals(2, actual[0].y)
        assertEquals(3, actual[1].x)
        assertEquals(4, actual[1].y)
    }

    @Test
    fun `it enumerates unknown struct fields`() {
        val payload = """
               <payload>
                   <x>1</x>
                   <z>unknown field</z>
                   <y>2</y>
               </payload>
           """.flatten().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = BasicStructTest.deserialize(deserializer)

        assertTrue(bst.unknownFieldCount == 1, "unknown field not enumerated")
    }

    class Nested2 {
        var list2: List<String>? = null
        var int2: Int? = null

        companion object {
            val LIST2_FIELD_DESCRIPTOR =
                SdkFieldDescriptor("list2", SerialKind.List, 0, XmlList(elementName = "element"))
            val INT2_FIELD_DESCRIPTOR = SdkFieldDescriptor("int2", SerialKind.Integer)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "nested2"
                field(LIST2_FIELD_DESCRIPTOR)
                field(INT2_FIELD_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): Nested2 {
                val nested2 = Nested2()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            LIST2_FIELD_DESCRIPTOR.index -> nested2.list2 =
                                deserializer.deserializeList(LIST2_FIELD_DESCRIPTOR) {
                                    val list = mutableListOf<String>()
                                    while (hasNextElement()) {
                                        list.add(deserializeString())
                                    }
                                    return@deserializeList list
                                }
                            INT2_FIELD_DESCRIPTOR.index -> nested2.int2 = deserializeInt()
                            // deeply nested unknown field
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                                skipValue()
                            }
                            null -> break@loop
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field during test"))
                        }
                    }
                }
                return nested2
            }
        }
    }

    class Nested {
        var nested2: Nested2? = null
        var bool2: Boolean? = null

        companion object {
            val NESTED2_FIELD_DESCRIPTOR = SdkFieldDescriptor("nested2", SerialKind.Struct)
            val BOOL2_FIELD_DESCRIPTOR = SdkFieldDescriptor("bool2", SerialKind.Boolean)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "nested"
                field(NESTED2_FIELD_DESCRIPTOR)
                field(BOOL2_FIELD_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): Nested {
                val nested = Nested()

                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            NESTED2_FIELD_DESCRIPTOR.index -> {
                                nested.nested2 = Nested2.deserialize(deserializer)
                            }
                            BOOL2_FIELD_DESCRIPTOR.index -> nested.bool2 = deserializeBool()
                            null -> break@loop
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field during test"))
                        }
                    }
                }

                return nested
            }
        }
    }

    class KitchenSinkTest {
        var intField: Int? = null
        var longField: Long? = null
        var shortField: Short? = null
        var boolField: Boolean? = null
        var strField: String? = null
        var listField: List<Int>? = null
        var doubleField: Double? = null
        var nestedField: Nested? = null
        var floatField: Float? = null
        var mapField: Map<String, String>? = null

        companion object {
            val INT_FIELD_DESCRIPTOR = SdkFieldDescriptor("int", SerialKind.Integer)
            val LONG_FIELD_DESCRIPTOR = SdkFieldDescriptor("long", SerialKind.Long)
            val SHORT_FIELD_DESCRIPTOR = SdkFieldDescriptor("short", SerialKind.Short)
            val BOOL_FIELD_DESCRIPTOR = SdkFieldDescriptor("bool", SerialKind.Boolean)
            val STR_FIELD_DESCRIPTOR = SdkFieldDescriptor("str", SerialKind.String)
            val LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor("list", SerialKind.List, 0, XmlList())
            val DOUBLE_FIELD_DESCRIPTOR = SdkFieldDescriptor("double", SerialKind.Double)
            val NESTED_FIELD_DESCRIPTOR = SdkFieldDescriptor("nested", SerialKind.Struct)
            val FLOAT_FIELD_DESCRIPTOR = SdkFieldDescriptor("float", SerialKind.Float)
            val MAP_FIELD_DESCRIPTOR =
                SdkFieldDescriptor("map", SerialKind.Map, 0, XmlMap("entry", "key", "value", true))

            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "payload"
                field(INT_FIELD_DESCRIPTOR)
                field(LONG_FIELD_DESCRIPTOR)
                field(SHORT_FIELD_DESCRIPTOR)
                field(BOOL_FIELD_DESCRIPTOR)
                field(STR_FIELD_DESCRIPTOR)
                field(LIST_FIELD_DESCRIPTOR)
                field(DOUBLE_FIELD_DESCRIPTOR)
                field(NESTED_FIELD_DESCRIPTOR)
                field(FLOAT_FIELD_DESCRIPTOR)
                field(MAP_FIELD_DESCRIPTOR)
            }
        }
    }

    @Test
    fun `it handles kitchen sink`() {
        val payload = """
           <?xml version="1.0" encoding="UTF-8" ?>
           <payload>
               <int>1</int>
               <long>2</long>
               <short>3</short>
               <bool>false</bool>
               <str>a string</str>
               <list>
                   <element>10</element>
                   <element>11</element>
                   <element>12</element>
               </list>
               <double>7.5</double>
               <nested>
                   <nested2>
                       <list2>
                           <element>x</element>
                           <element>y</element>
                       </list2>
                       <unknown>
                           <a>a</a>
                           <b>b</b>
                           <c>
                               <element>d</element>
                               <element>e</element>
                               <element>f</element>
                           </c>
                           <g>
                               <h>h</h>
                               <i>i</i>
                           </g>
                       </unknown>
                       <int2>4</int2>
                   </nested2>
                   <bool2>true</bool2>
               </nested>
               <float>0.2</float>
               <map>
                   <entry>
                       <key>key1</key>
                       <value>value1</value>
                   </entry>
                   <entry>
                       <key>key2</key>
                       <value>value2</value>
                   </entry>
               </map>
           </payload>
           """.flatten().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val sink = KitchenSinkTest()
        deserializer.deserializeStruct(KitchenSinkTest.OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    KitchenSinkTest.INT_FIELD_DESCRIPTOR.index -> sink.intField = deserializeInt()
                    KitchenSinkTest.LONG_FIELD_DESCRIPTOR.index -> sink.longField = deserializeLong()
                    KitchenSinkTest.SHORT_FIELD_DESCRIPTOR.index -> sink.shortField = deserializeShort()
                    KitchenSinkTest.BOOL_FIELD_DESCRIPTOR.index -> sink.boolField = deserializeBool()
                    KitchenSinkTest.STR_FIELD_DESCRIPTOR.index -> sink.strField = deserializeString()
                    KitchenSinkTest.LIST_FIELD_DESCRIPTOR.index -> sink.listField =
                        deserializer.deserializeList(KitchenSinkTest.LIST_FIELD_DESCRIPTOR) {
                            val list = mutableListOf<Int>()
                            while (hasNextElement()) {
                                list.add(deserializeInt())
                            }
                            return@deserializeList list
                        }
                    KitchenSinkTest.DOUBLE_FIELD_DESCRIPTOR.index -> sink.doubleField = deserializeDouble()
                    KitchenSinkTest.NESTED_FIELD_DESCRIPTOR.index -> sink.nestedField = Nested.deserialize(deserializer)
                    KitchenSinkTest.FLOAT_FIELD_DESCRIPTOR.index -> sink.floatField = deserializeFloat()
                    KitchenSinkTest.MAP_FIELD_DESCRIPTOR.index -> sink.mapField =
                        deserializer.deserializeMap(KitchenSinkTest.MAP_FIELD_DESCRIPTOR) {
                            val map = mutableMapOf<String, String>()
                            while (hasNextEntry()) {
                                val key = key()
                                val value = deserializeString()
                                map[key] = value
                            }
                            return@deserializeMap map
                        }
                    null -> break@loop
                    else -> throw XmlGenerationException(IllegalStateException("unexpected field during test"))
                }
            }
        }

        assertEquals(1, sink.intField)
        assertEquals(2L, sink.longField)
        assertEquals(3.toShort(), sink.shortField)
        assertEquals(false, sink.boolField)
        assertEquals("a string", sink.strField)
        sink.listField.shouldContainExactly(listOf(10, 11, 12))
        assertTrue(abs(sink.doubleField!! - 7.5) <= 0.0001)

        assertEquals(sink.nestedField!!.nested2!!.int2, 4)
        sink.nestedField!!.nested2!!.list2.shouldContainExactly(listOf("x", "y"))
        assertEquals(sink.nestedField!!.bool2, true)

        assertTrue(abs(sink.floatField!! - 0.2f) <= 0.0001f)
        val expectedMap = mapOf("key1" to "value1", "key2" to "value2")
        sink.mapField!!.shouldContainExactly(expectedMap)
    }

    class HostedZoneConfig private constructor(builder: BuilderImpl) {
        val comment: String? = builder.comment

        companion object {
            val COMMENT_DESCRIPTOR = SdkFieldDescriptor("Comment", SerialKind.String)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "HostedZoneConfig"
                field(COMMENT_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): HostedZoneConfig {
                val builder = BuilderImpl()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            COMMENT_DESCRIPTOR.index -> builder.comment = deserializeString()
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                            }
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field index in HostedZoneConfig deserializer"))
                        }
                    }
                }
                return HostedZoneConfig(builder)
            }

            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
        }

        interface Builder {
            fun build(): HostedZoneConfig
            // TODO - Java fill in Java builder
        }

        interface DslBuilder {
            var comment: String?
        }

        private class BuilderImpl : Builder, DslBuilder {
            override var comment: String? = null

            override fun build(): HostedZoneConfig = HostedZoneConfig(this)
        }
    }

    class CreateHostedZoneRequest private constructor(builder: BuilderImpl) {
        val name: String? = builder.name
        val callerReference: String? = builder.callerReference
        val hostedZoneConfig: HostedZoneConfig? = builder.hostedZoneConfig

        companion object {
            val NAME_DESCRIPTOR = SdkFieldDescriptor("Name", SerialKind.String)
            val CALLER_REFERENCE_DESCRIPTOR = SdkFieldDescriptor("CallerReference", SerialKind.String)
            val HOSTED_ZONE_DESCRIPTOR = SdkFieldDescriptor("HostedZoneConfig", SerialKind.Struct)

            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "CreateHostedZoneRequest"
                field(NAME_DESCRIPTOR)
                field(CALLER_REFERENCE_DESCRIPTOR)
                field(HOSTED_ZONE_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): CreateHostedZoneRequest {
                val builder = BuilderImpl()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            NAME_DESCRIPTOR.index -> builder.name = deserializeString()
                            CALLER_REFERENCE_DESCRIPTOR.index -> builder.callerReference = deserializeString()
                            HOSTED_ZONE_DESCRIPTOR.index -> builder.hostedZoneConfig =
                                HostedZoneConfig.deserialize(deserializer)
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> skipValue()
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field index in CreateHostedZoneRequest deserializer"))
                        }
                    }
                }
                return builder.build()
            }

            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
        }

        interface Builder {
            fun build(): CreateHostedZoneRequest
            // TODO - Java fill in Java builder
        }

        interface DslBuilder {
            var name: String?
            var callerReference: String?
            var hostedZoneConfig: HostedZoneConfig?
        }

        private class BuilderImpl : Builder, DslBuilder {
            override var name: String? = null
            override var callerReference: String? = null
            override var hostedZoneConfig: HostedZoneConfig? = null

            override fun build(): CreateHostedZoneRequest = CreateHostedZoneRequest(this)
        }
    }

    @Test
    fun `it handles Route 53 XML`() {
        val testXml = """
               <?xml version="1.0" encoding="UTF-8"?><!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0.
                 -->

               <CreateHostedZoneRequest xmlns="https://route53.amazonaws.com/doc/2013-04-01/">
                   <Name>java.sdk.com.</Name>
                   <CallerReference>a322f752-8156-4746-8c04-e174ca1f51ce</CallerReference>
                   <HostedZoneConfig>
                       <Comment>comment</Comment>
                   </HostedZoneConfig>
               </CreateHostedZoneRequest>
           """.flatten()

        val unit = XmlDeserializer(testXml.encodeToByteArray())

        val createHostedZoneRequest = CreateHostedZoneRequest.deserialize(unit)

        assertTrue(createHostedZoneRequest.name == "java.sdk.com.")
        assertTrue(createHostedZoneRequest.callerReference == "a322f752-8156-4746-8c04-e174ca1f51ce")
        assertNotNull(createHostedZoneRequest.hostedZoneConfig)
        assertTrue(createHostedZoneRequest.hostedZoneConfig.comment == "comment")
    }
}

// Remove linefeeds in a string
private fun String.flatten(): String =
    this.trimIndent().lines().joinToString(separator = "") { line -> line.trim() }
