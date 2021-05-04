/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.formurl

import software.aws.clientrt.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals

class FormUrlSerializerTest {

    data class PrimitiveStructTest(
        val boolean: Boolean = true,
        val byte: Byte = 1,
        val short: Short = 2,
        val int: Int = 3,
        val long: Long = 4,
        val float: Float = 5.1f,
        val double: Double = 6.2,
        val char: Char = 'c',
        val string: String = "foo",
        val listInt: List<Int> = listOf(5, 6, 7)

    ) : SdkSerializable {
        companion object {
            val descriptorBoolean = SdkFieldDescriptor(SerialKind.Boolean, FormUrlSerialName("boolean"))
            val descriptorByte = SdkFieldDescriptor(SerialKind.Byte, FormUrlSerialName("byte"))
            val descriptorShort = SdkFieldDescriptor(SerialKind.Short, FormUrlSerialName("short"))
            val descriptorInt = SdkFieldDescriptor(SerialKind.Integer, FormUrlSerialName("int"))
            val descriptorLong = SdkFieldDescriptor(SerialKind.Long, FormUrlSerialName("long"))
            val descriptorFloat = SdkFieldDescriptor(SerialKind.Float, FormUrlSerialName("float"))
            val descriptorDouble = SdkFieldDescriptor(SerialKind.Double, FormUrlSerialName("double"))
            val descriptorChar = SdkFieldDescriptor(SerialKind.Char, FormUrlSerialName("char"))
            val descriptorString = SdkFieldDescriptor(SerialKind.String, FormUrlSerialName("string"))
            val descriptorListInt = SdkFieldDescriptor(SerialKind.List, FormUrlSerialName("listInt"))
        }

        override fun serialize(serializer: Serializer) {
            val objDescriptor = SdkObjectDescriptor.build {}
            serializer.serializeStruct(objDescriptor) {
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

    class A(private val b: B) : SdkSerializable {
        companion object {
            val descriptorB: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Struct, FormUrlSerialName("b"))

            val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
                FormUrlSerialName("a")
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
            val descriptorValue = SdkFieldDescriptor(SerialKind.Integer, FormUrlSerialName("v"))

            val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
                FormUrlSerialName("b")
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
    fun itSerializesStructs() {
        val struct = PrimitiveStructTest()
        val serializer = FormUrlSerializer()
        struct.serialize(serializer)
        val expected = """
            boolean=true
            &byte=1
            &short=2
            &int=3
            &long=4
            &float=5.1
            &double=6.2
            &char=c
            &string=foo
            &listInt.member.1=5
            &listInt.member.2=6
            &listInt.member.3=7
        """.trimIndent().replace("\n", "")
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun itSerializesEmptyStrings() {
        // see `string` from https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#x-www-form-urlencoded-shape-serialization
        val intValue = SdkFieldDescriptor(SerialKind.String, FormUrlSerialName("i"))
        val str1Value = SdkFieldDescriptor(SerialKind.String, FormUrlSerialName("s1"))
        val str2Value = SdkFieldDescriptor(SerialKind.String, FormUrlSerialName("s2"))

        val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
            FormUrlSerialName("b")
            field(intValue)
            field(str1Value)
            field(str2Value)
        }

        val serializer = FormUrlSerializer()
        serializer.serializeStruct(objectDescriptor) {
            field(intValue, 2)
            field(str1Value, "")
            field(str2Value, "foo")
        }

        val expected = """
            i=2
            &s1=
            &s2=foo
        """.trimIndent().replace("\n", "")
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }

    data class ListInput(
        val primitiveList: List<String>?,
        val structList: List<B>?
    ) {
        fun serialize(
            serializer: Serializer,
            PRIMITIVE_LIST_DESCRIPTOR: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.List, FormUrlSerialName("PrimitiveList")),
            STRUCT_LIST_DESCRIPTOR: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.List, FormUrlSerialName("StructList"))
        ) {
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                field(PRIMITIVE_LIST_DESCRIPTOR)
                field(STRUCT_LIST_DESCRIPTOR)
            }
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (primitiveList != null) {
                    listField(PRIMITIVE_LIST_DESCRIPTOR) {
                        for (el0 in primitiveList) {
                            serializeString(el0)
                        }
                    }
                }
                if (structList != null) {
                    listField(STRUCT_LIST_DESCRIPTOR) {
                        for (el0 in structList) {
                            serializeSdkSerializable(el0)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun itSerializesLists() {
        val input = ListInput(
            primitiveList = listOf("foo", "bar"),
            structList = listOf(B(5), B(6), B(7))
        )

        val expected = """
            PrimitiveList.member.1=foo
            &PrimitiveList.member.2=bar
            &StructList.member.1.v=5
            &StructList.member.2.v=6
            &StructList.member.3.v=7
        """.trimIndent().replace("\n", "")

        val serializer = FormUrlSerializer()
        input.serialize(serializer)
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun itSerializesFlattenedLists() {
        // xmlFlattened() lists
        val input = ListInput(
            primitiveList = listOf("foo", "bar"),
            structList = listOf(B(5), B(6), B(7))
        )

        val expected = """
            PrimitiveList.1=foo
            &PrimitiveList.2=bar
            &StructList.member.1.v=5
            &StructList.member.2.v=6
            &StructList.member.3.v=7
        """.trimIndent().replace("\n", "")

        val serializer = FormUrlSerializer()

        val primitiveListDescriptor = SdkFieldDescriptor(SerialKind.List, FormUrlFlattened, FormUrlSerialName("PrimitiveList"))
        input.serialize(serializer, primitiveListDescriptor)
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun itSerializesListsWithRenamedMember() {
        // xmlName() trait on list member
        TODO("not implemented")
    }

    @Test
    fun itSerializesClassWithNestedClassField() {
        val a = A(
            B(2)
        )
        val serializer = FormUrlSerializer()
        a.serialize(serializer)
        assertEquals("""b.v=2""", serializer.toByteArray().decodeToString())
    }

    data class MapInput(
        val primitiveMap: Map<String, String>? = null,
        val structMap: Map<String, B>? = null,
        val mapOfLists: Map<String, List<String>>? = null,
    ) {

        fun serialize(
            serializer: Serializer,
            PRIMITIVE_MAP_DESCRIPTOR: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Map, FormUrlSerialName("PrimitiveMap")),
            STRUCT_MAP_DESCRIPTOR: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Map, FormUrlSerialName("StructMap")),
            MAP_OF_LISTS_DESCRIPTOR: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.Map, FormUrlSerialName("MapOfLists")),
            // serialName of this nested descriptor should be ignored
            MAP_OF_LISTS_CO_DESCRIPTOR: SdkFieldDescriptor = SdkFieldDescriptor(SerialKind.List, FormUrlSerialName("ChildStringList")),
        ) {
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                field(PRIMITIVE_MAP_DESCRIPTOR)
                field(STRUCT_MAP_DESCRIPTOR)
                field(MAP_OF_LISTS_DESCRIPTOR)
            }
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (primitiveMap != null) {
                    mapField(PRIMITIVE_MAP_DESCRIPTOR) {
                        primitiveMap.forEach { (key, value) ->
                            entry(key, value)
                        }
                    }
                }
                if (structMap != null) {
                    mapField(STRUCT_MAP_DESCRIPTOR) {
                        structMap.forEach { (key, value) ->
                            entry(key, value)
                        }
                    }
                }
                if (mapOfLists != null) {
                    mapField(MAP_OF_LISTS_DESCRIPTOR) {
                        mapOfLists.forEach { (key, value) ->
                            listEntry(key, MAP_OF_LISTS_CO_DESCRIPTOR) {
                                for (el1 in value) {
                                    serializeString(el1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun itSerializesMaps() {
        val input = MapInput(
            primitiveMap = mapOf(
                "k1" to "v1",
                "k2" to "v2",
            ),
            structMap = mapOf(
                "b1" to B(7),
                "b2" to B(8),
                "b3" to B(9),
            )
        )

        val expected = """
            PrimitiveMap.entry.1.key=k1
            &PrimitiveMap.entry.1.value=v1
            &PrimitiveMap.entry.2.key=k2
            &PrimitiveMap.entry.2.value=v2
            &StructMap.entry.1.key=b1
            &StructMap.entry.1.value.v=7
            &StructMap.entry.2.key=b2
            &StructMap.entry.2.value.v=8
            &StructMap.entry.3.key=b3
            &StructMap.entry.3.value.v=9
        """.trimIndent().replace("\n", "")

        val serializer = FormUrlSerializer()
        input.serialize(serializer)
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun itSerializesMapOfLists() {
        val input = MapInput(
            mapOfLists = mapOf(
                "foo" to listOf("A", "B"),
                "bar" to listOf("C", "D")
            )
        )

        val expected = """
            MapOfLists.entry.1.key=foo
            &MapOfLists.entry.1.value.member.1=A
            &MapOfLists.entry.1.value.member.2=B
            &MapOfLists.entry.2.key=bar
            &MapOfLists.entry.2.value.member.1=C
            &MapOfLists.entry.2.value.member.2=D
        """.trimIndent().replace("\n", "")

        val serializer = FormUrlSerializer()
        input.serialize(serializer)
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }

    data class MapOfMapsInput(val input: Map<String, Map<String, String>>) {
        companion object {
            val MAP_OF_MAP_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, FormUrlSerialName("MapOfMaps"))
            val MAP_OF_MAP_C0_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, FormUrlSerialName("ChildMapOfMaps"))

            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                field(MAP_OF_MAP_DESCRIPTOR)
                field(MAP_OF_MAP_C0_DESCRIPTOR)
            }
        }

        fun serialize(serializer: Serializer) {
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                mapField(MAP_OF_MAP_DESCRIPTOR) {
                    input.forEach { (key, value) ->
                        mapEntry(key, MAP_OF_MAP_C0_DESCRIPTOR) {
                            value.forEach { (key1, value1) -> entry(key1, value1) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun itSerializesMapOfMapOfPrimitive() {

        val expected = """
            MapOfMaps.entry.1.key=foo
            &MapOfMaps.entry.1.value.entry.1.key=k1
            &MapOfMaps.entry.1.value.entry.1.value=v1
            &MapOfMaps.entry.1.value.entry.2.key=k2
            &MapOfMaps.entry.1.value.entry.2.value=v2
        """.trimIndent().replace("\n", "")

        val input = MapOfMapsInput(
            mapOf(
                "foo" to mapOf("k1" to "v1", "k2" to "v2")
            )
        )
        val serializer = FormUrlSerializer()
        input.serialize(serializer)
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun itSerializesFlattenedMaps() {
        val input = MapInput(
            primitiveMap = mapOf(
                "k1" to "v1",
                "k2" to "v2",
            )
        )

        val expected = """
            PrimitiveMap.1.key=k1
            &PrimitiveMap.1.value=v1
            &PrimitiveMap.2.key=k2
            &PrimitiveMap.2.value=v2
        """.trimIndent().replace("\n", "")

        val serializer = FormUrlSerializer()
        val primitiveMapDescriptor = SdkFieldDescriptor(SerialKind.Map, FormUrlSerialName("PrimitiveMap"), FormUrlFlattened)
        input.serialize(serializer, primitiveMapDescriptor)
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun itSerializesRenamedMaps() {
        // map with xmlName key/value overrides
        TODO("not implemented")
    }

    @Test
    fun itSerializesQueryLiterals() {
        // test SdkObjectDescriptor with query literals trait

        val intValue = SdkFieldDescriptor(SerialKind.String, FormUrlSerialName("i"))
        val str1Value = SdkFieldDescriptor(SerialKind.String, FormUrlSerialName("s1"))

        val objectDescriptor: SdkObjectDescriptor = SdkObjectDescriptor.build {
            FormUrlSerialName("b")
            trait(QueryLiteral("lit1", "v1"))
            trait(QueryLiteral("lit2", "v2"))
            field(intValue)
            field(str1Value)
        }

        val serializer = FormUrlSerializer()
        serializer.serializeStruct(objectDescriptor) {
            field(intValue, 2)
            field(str1Value, "foo")
        }

        val expected = """
            lit1=v1
            &lit2=v2
            &i=2
            &s1=foo
        """.trimIndent().replace("\n", "")
        val actual = serializer.toByteArray().decodeToString()
        assertEquals(expected, actual)
    }
}
