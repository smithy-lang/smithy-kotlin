/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class JsonSerializerTest {

    @Test
    fun canSerializeClassWithClassField() {
        val a = A(
            B(2)
        )
        val json = JsonSerializer()
        a.serialize(json)
        assertEquals("""{"b":{"value":2}}""", json.toByteArray().decodeToString())
    }

    class A(private val b: B) : SdkSerializable {
        companion object {
            val descriptorB: SdkFieldDescriptor = SdkFieldDescriptor.fromSerialName("b", SerialKind.Struct)
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(AnonymousStructFieldDescriptor) {
                field(descriptorB, b)
            }
        }
    }

    data class B(private val value: Int) : SdkSerializable {
        companion object {
            val descriptorValue = SdkFieldDescriptor.fromSerialName("value", SerialKind.Integer)
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(AnonymousStructFieldDescriptor) {
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
        val json = JsonSerializer()
        json.serializeList(AnonymousStructFieldDescriptor) {
            for (value in obj) {
                value.serialize(json)
            }
        }
        assertEquals("""[{"value":1},{"value":2},{"value":3}]""", json.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeMap() {
        val objs = mapOf(
            "A1" to A(
                B(1)
            ),
            "A2" to A(
                B(
                    2
                )
            ),
            "A3" to A(
                B(
                    3
                )
            )
        )
        val json = JsonSerializer()
        json.serializeMap(AnonymousStructFieldDescriptor) {
            for (obj in objs) {
                entry(obj.key, obj.value)
            }
        }
        assertEquals("""{"A1":{"b":{"value":1}},"A2":{"b":{"value":2}},"A3":{"b":{"value":3}}}""", json.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeMapOfLists() {
        val objs = mapOf(
            "A1" to listOf("a", "b", "c"),
            "A2" to listOf("d", "e", "f"),
            "A3" to listOf("g", "h", "i")
        )
        val json = JsonSerializer()
        json.serializeMap(AnonymousStructFieldDescriptor) {
            for (obj in objs) {
                listEntry(obj.key, AnonymousStructFieldDescriptor) {
                    for (v in obj.value) {
                        serializeString(v)
                    }
                }
            }
        }
        assertEquals("""{"A1":["a","b","c"],"A2":["d","e","f"],"A3":["g","h","i"]}""", json.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeListOfLists() {
        val objs = listOf(
            listOf("a", "b", "c"),
            listOf("d", "e", "f"),
            listOf("g", "h", "i")
        )
        val json = JsonSerializer()
        json.serializeList(AnonymousStructFieldDescriptor) {
            for (obj in objs) {
                json.serializeList(AnonymousStructFieldDescriptor) {
                    for (v in obj) {
                        serializeString(v)
                    }
                }
            }
        }
        assertEquals("""[["a","b","c"],["d","e","f"],["g","h","i"]]""", json.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeListOfMaps() {
        val objs = listOf(
            mapOf("a" to "b", "c" to "d"),
            mapOf("e" to "f", "g" to "h"),
            mapOf("i" to "j", "k" to "l"),
        )
        val json = JsonSerializer()
        json.serializeList(AnonymousStructFieldDescriptor) {
            for (obj in objs) {
                json.serializeMap(AnonymousStructFieldDescriptor) {
                    for (v in obj) {
                        entry(v.key, v.value)
                    }
                }
            }
        }
        assertEquals("""[{"a":"b","c":"d"},{"e":"f","g":"h"},{"i":"j","k":"l"}]""", json.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeMapOfMaps() {
        val objs = mapOf(
            "A1" to mapOf("a" to "b", "c" to "d"),
            "A2" to mapOf("e" to "f", "g" to "h"),
            "A3" to mapOf("i" to "j", "k" to "l"),
        )
        val json = JsonSerializer()
        json.serializeMap(AnonymousStructFieldDescriptor) {
            for (obj in objs) {
                mapEntry(obj.key, AnonymousStructFieldDescriptor) {
                    for (v in obj.value) {
                        entry(v.key, v.value)
                    }
                }
            }
        }
        assertEquals("""{"A1":{"a":"b","c":"d"},"A2":{"e":"f","g":"h"},"A3":{"i":"j","k":"l"}}""", json.toByteArray().decodeToString())
    }

    @Test
    fun canSerializeAllPrimitives() {
        val json = JsonSerializer()
        data.serialize(json)

        assertEquals("""{"boolean":true,"boolean":null,"byte":10,"short":20,"int":30,"long":40,"float":50.0,"double":60.0,"char":"A","string":"Str0","listInt":[1,2,3]}""", json.toByteArray().decodeToString())
    }
}

data class Primitives(
    val unit: Unit,
    val boolean: Boolean,
    val byte: Byte,
    val short: Short,
    val int: Int,
    val long: Long,
    val float: Float,
    val double: Double,
    val char: Char,
    val string: String,
    val listInt: List<Int>
) : SdkSerializable {
    companion object {
        val descriptorBoolean = SdkFieldDescriptor.fromSerialName("boolean", SerialKind.Boolean)
        val descriptorByte = SdkFieldDescriptor.fromSerialName("byte", SerialKind.Byte)
        val descriptorShort = SdkFieldDescriptor.fromSerialName("short", SerialKind.Short)
        val descriptorInt = SdkFieldDescriptor.fromSerialName("int", SerialKind.Integer)
        val descriptorLong = SdkFieldDescriptor.fromSerialName("long", SerialKind.Long)
        val descriptorFloat = SdkFieldDescriptor.fromSerialName("float", SerialKind.Float)
        val descriptorDouble = SdkFieldDescriptor.fromSerialName("double", SerialKind.Double)
        val descriptorChar = SdkFieldDescriptor.fromSerialName("char", SerialKind.Char)
        val descriptorString = SdkFieldDescriptor.fromSerialName("string", SerialKind.String)
        val descriptorListInt = SdkFieldDescriptor.fromSerialName("listInt", SerialKind.List)
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(AnonymousStructFieldDescriptor) {
            field(descriptorBoolean, boolean)
            nullField(descriptorBoolean)
            field(descriptorByte, byte)
            field(descriptorShort, short)
            field(descriptorInt, int)
            field(descriptorLong, long)
            field(descriptorFloat, float)
            field(descriptorDouble, double)
            field(descriptorChar, char)
            field(descriptorString, string)
            listField(descriptorListInt) {
                for (value in listInt) {
                    serializeInt(value)
                }
            }
        }
    }
}

val data = Primitives(
    Unit, true, 10, 20, 30, 40, 50f, 60.0, 'A', "Str0",
    listOf(1, 2, 3)
)
