/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.content.buildDocument
import aws.smithy.kotlin.runtime.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals

private val testAnonObjDescriptor = SdkObjectDescriptor.build { }

@OptIn(ExperimentalStdlibApi::class)
class JsonSerializerTest {

    @Test
    fun canSerializeClassWithClassField() {
        val a = A(
            B(2),
        )
        val json = JsonSerializer()
        a.serialize(json)
        assertEquals("""{"b":{"value":2}}""", json.toByteArray().decodeToString())
    }

    class A(private val b: B) : SdkSerializable {
        companion object {
            val descriptorB: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Struct, JsonSerialName("b"))
            val objDescriptor = SdkObjectDescriptor.build {
                field(descriptorB)
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(objDescriptor) {
                field(descriptorB, b)
            }
        }
    }

    data class B(private val value: Int) : SdkSerializable {
        companion object {
            val descriptorValue = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("value"))
            val objDescriptor = SdkObjectDescriptor.build {
                field(descriptorValue)
            }
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(objDescriptor) {
                field(descriptorValue, value)
            }
        }
    }

    @Test
    fun canSerializeListOfClasses() {
        val obj = listOf(
            B(1),
            B(2),
            B(3),
        )
        val json = JsonSerializer()
        json.serializeList(testAnonObjDescriptor) {
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
                B(1),
            ),
            "A2" to A(
                B(
                    2,
                ),
            ),
            "A3" to A(
                B(
                    3,
                ),
            ),
        )
        val json = JsonSerializer()
        json.serializeMap(testAnonObjDescriptor) {
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
            "A3" to listOf("g", "h", "i"),
        )
        val json = JsonSerializer()
        json.serializeMap(testAnonObjDescriptor) {
            for (obj in objs) {
                listEntry(obj.key, testAnonObjDescriptor) {
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
            listOf("g", "h", "i"),
        )
        val json = JsonSerializer()
        json.serializeList(testAnonObjDescriptor) {
            for (obj in objs) {
                json.serializeList(testAnonObjDescriptor) {
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
        json.serializeList(testAnonObjDescriptor) {
            for (obj in objs) {
                json.serializeMap(testAnonObjDescriptor) {
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
        json.serializeMap(testAnonObjDescriptor) {
            for (obj in objs) {
                mapEntry(obj.key, testAnonObjDescriptor) {
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

    @Test
    fun canSerializeNonFiniteDoubles() {
        val tests = mapOf(
            Double.NEGATIVE_INFINITY to "\"-Infinity\"",
            Double.POSITIVE_INFINITY to "\"Infinity\"",
            Double.NaN to "\"NaN\"",
        )

        for ((input, expected) in tests) {
            val json = JsonSerializer()
            json.serializeDouble(input)
            val actual = json.toByteArray().decodeToString()

            assertEquals(expected, actual)
        }
    }

    @Test
    fun canSerializeNonFiniteFloats() {
        val tests = mapOf(
            Float.NEGATIVE_INFINITY to "\"-Infinity\"",
            Float.POSITIVE_INFINITY to "\"Infinity\"",
            Float.NaN to "\"NaN\"",
        )

        for ((input, expected) in tests) {
            val json = JsonSerializer()
            json.serializeFloat(input)
            val actual = json.toByteArray().decodeToString()

            assertEquals(expected, actual)
        }
    }

    private fun testSerializeDocument(doc: Document?, expected: String) {
        val s = JsonSerializer()
        val documentField = SdkFieldDescriptor(SerialKind.Document, JsonSerialName("SerializedDocument"))
        val struct = SdkObjectDescriptor.build {
            field(documentField)
        }

        s.serializeStruct(struct) {
            field(documentField, doc)
        }

        val actual = s.toByteArray().decodeToString()
        assertEquals("{\"SerializedDocument\":$expected}", actual)
    }

    @Test
    fun canSerializeDocumentNumberField() =
        testSerializeDocument(
            Document(10.5),
            "10.5",
        )

    @Test
    fun canSerializeDocumentStringField() =
        testSerializeDocument(
            Document("foo"),
            "\"foo\"",
        )

    @Test
    fun canSerializeDocumentBooleanField() =
        testSerializeDocument(
            Document(false),
            "false",
        )

    @Test
    fun canSerializeDocumentNullField() =
        testSerializeDocument(
            null,
            "null",
        )

    @Test
    fun canSerializeDocumentListField() =
        testSerializeDocument(
            Document.List(
                listOf(
                    Document(1),
                    Document("foo"),
                    Document(true),
                    null,
                ),
            ),
            "[1,\"foo\",true,null]",
        )

    @Test
    fun canSerializeDocumentMapField() =
        testSerializeDocument(
            buildDocument {
                "number" to 12L
                "string" to "foo"
                "bool" to true
                "null" to null
            },
            """{"number":12,"string":"foo","bool":true,"null":null}""",
        )

    @Test
    fun canSerializeComplexDocument() {
        val doc = buildDocument {
            "number" to 12
            "string" to "foo"
            "bool" to true
            "null" to null
            "list" to buildList {
                add(12.0)
                add("foo")
                add(true)
                add(null)
                add(
                    buildList {
                        add(12.0)
                        add("foo")
                        add(true)
                        add(null)
                    },
                )
                add(
                    buildDocument {
                        "number" to 12.0
                        "string" to "foo"
                        "bool" to true
                        "null" to null
                    },
                )
            }
            "map" to buildDocument {
                "number" to 12L
                "string" to "foo"
                "bool" to false
                "null" to null
                "list" to buildList {
                    add(12L)
                    add("foo")
                    add(false)
                    add(null)
                }
                "map" to buildDocument {
                    "number" to 12L
                    "string" to "foo"
                    "bool" to false
                    "null" to null
                }
            }
        }
        val expected = """
        {
            "number": 12,
            "string": "foo",
            "bool": true,
            "null": null,
            "list": [
                12.0,
                "foo",
                true,
                null,
                [
                    12.0,
                    "foo",
                    true,
                    null
                ],
                {
                    "number": 12.0,
                    "string": "foo",
                    "bool": true,
                    "null": null
                }
            ],
            "map": {
                "number": 12,
                "string": "foo",
                "bool": false,
                "null": null,
                "list": [
                    12,
                    "foo",
                    false,
                    null
                ],
                "map": {
                    "number": 12,
                    "string": "foo",
                    "bool": false,
                    "null": null
                }
            }
        }
        """
            .replace("\n", "")
            .replace(" ", "")

        testSerializeDocument(doc, expected)
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
    val listInt: List<Int>,
) : SdkSerializable {
    companion object {
        val descriptorBoolean = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("boolean"))
        val descriptorByte = SdkFieldDescriptor(SerialKind.Byte, JsonSerialName("byte"))
        val descriptorShort = SdkFieldDescriptor(SerialKind.Short, JsonSerialName("short"))
        val descriptorInt = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("int"))
        val descriptorLong = SdkFieldDescriptor(SerialKind.Long, JsonSerialName("long"))
        val descriptorFloat = SdkFieldDescriptor(SerialKind.Float, JsonSerialName("float"))
        val descriptorDouble = SdkFieldDescriptor(SerialKind.Double, JsonSerialName("double"))
        val descriptorChar = SdkFieldDescriptor(SerialKind.Char, JsonSerialName("char"))
        val descriptorString = SdkFieldDescriptor(SerialKind.String, JsonSerialName("string"))
        val descriptorListInt = SdkFieldDescriptor(SerialKind.List, JsonSerialName("listInt"))
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(testAnonObjDescriptor) {
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
    listOf(1, 2, 3),
)
