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
package software.aws.clientrt.test

import androidx.test.runner.AndroidJUnit4
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import org.junit.Test
import org.junit.runner.RunWith
import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.json.JsonDeserializer
import software.aws.clientrt.serde.json.JsonSerializer
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Serde interfaces have dependencies on JVM libs, ensure they run on device
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalStdlibApi::class)
class SerdeTest {
    class Nested : SdkSerializable {
        var bool2: Boolean? = null

        companion object {
            val BOOL2_FIELD_DESCRIPTOR = SdkFieldDescriptor("bool2", SerialKind.Boolean)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
                field(BOOL2_FIELD_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): Nested {
                val struct = deserializer.deserializeStruct(OBJ_DESCRIPTOR)
                val nested = Nested()
                loop@ while (true) {
                    when (struct.findNextFieldIndex()) {
                        BOOL2_FIELD_DESCRIPTOR.index -> nested.bool2 = deserializer.deserializeBool()
                        null -> break@loop
                        else -> throw RuntimeException("unexpected field during test")
                    }
                }
                return nested
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                bool2?.let { field(BOOL2_FIELD_DESCRIPTOR, it) }
            }
        }
    }

    class AllTypesTest {
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
            val LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor("list", SerialKind.List)
            val DOUBLE_FIELD_DESCRIPTOR = SdkFieldDescriptor("double", SerialKind.Double)
            val NESTED_FIELD_DESCRIPTOR = SdkFieldDescriptor("nested", SerialKind.Struct)
            val FLOAT_FIELD_DESCRIPTOR = SdkFieldDescriptor("float", SerialKind.Float)
            val MAP_FIELD_DESCRIPTOR = SdkFieldDescriptor("map", SerialKind.Map)

            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
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
    fun jsonDeserializeTest() {
        val payload = """
        {
            "int": 1,
            "long": 2,
            "short": 3,
            "bool": false,
            "str": "a string",
            "list": [10, 11, 12],
            "double": 7.5,
            "nested": {
                "bool2": true
            },
            "float": 0.2,
            "map": {
                "key1": "value1",
                "key2": "value2"
            }
        }
        """.trimIndent().encodeToByteArray()

        val deserializer = JsonDeserializer(payload)
        val struct = deserializer.deserializeStruct(AllTypesTest.OBJ_DESCRIPTOR)
        val sink = AllTypesTest()
        loop@ while (true) {
            when (struct.findNextFieldIndex()) {
                AllTypesTest.INT_FIELD_DESCRIPTOR.index -> sink.intField = struct.deserializeInt()
                AllTypesTest.LONG_FIELD_DESCRIPTOR.index -> sink.longField = struct.deserializeLong()
                AllTypesTest.SHORT_FIELD_DESCRIPTOR.index -> sink.shortField = struct.deserializeShort()
                AllTypesTest.BOOL_FIELD_DESCRIPTOR.index -> sink.boolField = struct.deserializeBool()
                AllTypesTest.STR_FIELD_DESCRIPTOR.index -> sink.strField = struct.deserializeString()
                AllTypesTest.LIST_FIELD_DESCRIPTOR.index -> sink.listField = deserializer.deserializeList(AllTypesTest.LIST_FIELD_DESCRIPTOR) {
                    val list = mutableListOf<Int>()
                    while (hasNextElement()) {
                        list.add(deserializeInt())
                    }
                    return@deserializeList list
                }
                AllTypesTest.DOUBLE_FIELD_DESCRIPTOR.index -> sink.doubleField = struct.deserializeDouble()
                AllTypesTest.NESTED_FIELD_DESCRIPTOR.index -> sink.nestedField = Nested.deserialize(deserializer)
                AllTypesTest.FLOAT_FIELD_DESCRIPTOR.index -> sink.floatField = struct.deserializeFloat()
                AllTypesTest.MAP_FIELD_DESCRIPTOR.index -> sink.mapField = deserializer.deserializeMap(AllTypesTest.MAP_FIELD_DESCRIPTOR) {
                    val map = mutableMapOf<String, String>()
                    while (hasNextEntry()) {
                        map[key()] = deserializeString()
                    }
                    return@deserializeMap map
                }
                null -> break@loop
                else -> throw RuntimeException("unexpected field during test")
            }
        }

        assertEquals(1, sink.intField)
        assertEquals(2L, sink.longField)
        assertEquals(3.toShort(), sink.shortField)
        assertEquals(false, sink.boolField)
        assertEquals("a string", sink.strField)
        sink.listField.shouldContainExactly(listOf(10, 11, 12))
        assertTrue(abs(sink.doubleField!! - 7.5) <= 0.0001)
        assertEquals(sink.nestedField!!.bool2, true)
        assertTrue(abs(sink.floatField!! - 0.2f) <= 0.0001f)
        val expectedMap = mapOf("key1" to "value1", "key2" to "value2")
        sink.mapField!!.shouldContainExactly(expectedMap)
    }

    @Test
    fun jsonSerializeTest() {
        val serializer = JsonSerializer()
        serializer.serializeStruct(AllTypesTest.OBJ_DESCRIPTOR) {
            field(AllTypesTest.INT_FIELD_DESCRIPTOR, 1)
            field(AllTypesTest.SHORT_FIELD_DESCRIPTOR, 2.toShort())
            field(AllTypesTest.LONG_FIELD_DESCRIPTOR, 3L)
            field(AllTypesTest.BOOL_FIELD_DESCRIPTOR, true)
            field(AllTypesTest.STR_FIELD_DESCRIPTOR, "hello world")
            val nested = Nested()
            nested.bool2 = false
            field(AllTypesTest.NESTED_FIELD_DESCRIPTOR, nested)
            serializer.listField(AllTypesTest.LIST_FIELD_DESCRIPTOR) {
                serializeInt(1)
                serializeInt(2)
                serializeInt(3)
            }

            serializer.mapField(AllTypesTest.MAP_FIELD_DESCRIPTOR) {
                entry("key1", "value1")
                entry("key2", "value2")
            }
        }

        val contents = serializer.toByteArray()
        assertTrue(contents.isNotEmpty())
    }

}