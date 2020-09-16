/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import kotlin.math.abs
import kotlin.test.*
import software.aws.clientrt.serde.*

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerKitchenSinkTest {

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
           """.trimIndent().encodeToByteArray()

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
}
