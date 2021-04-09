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
            val descriptorBoolean = SdkFieldDescriptor("boolean", SerialKind.Boolean)
            val descriptorByte = SdkFieldDescriptor("byte", SerialKind.Byte)
            val descriptorShort = SdkFieldDescriptor("short", SerialKind.Short)
            val descriptorInt = SdkFieldDescriptor("int", SerialKind.Integer)
            val descriptorLong = SdkFieldDescriptor("long", SerialKind.Long)
            val descriptorFloat = SdkFieldDescriptor("float", SerialKind.Float)
            val descriptorDouble = SdkFieldDescriptor("double", SerialKind.Double)
            val descriptorChar = SdkFieldDescriptor("char", SerialKind.Char)
            val descriptorString = SdkFieldDescriptor("string", SerialKind.String)
            val descriptorListInt = SdkFieldDescriptor("listInt", SerialKind.List)
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(SdkFieldDescriptor.ANONYMOUS_DESCRIPTOR) {
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

    data class ListInput(val primitiveList: List<String>?, val structList: List<B>?) {
        companion object {
            val PRIMITIVE_LIST_DESCRIPTOR = SdkFieldDescriptor("PrimitiveList", SerialKind.List)
            val STRUCT_LIST_DESCRIPTOR = SdkFieldDescriptor("StructList", SerialKind.List)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                field(PRIMITIVE_LIST_DESCRIPTOR)
                field(STRUCT_LIST_DESCRIPTOR)
            }
        }

        fun serialize(serializer: Serializer) {
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
        TODO("not implemented")
    }

    @Test
    fun itSerializesRenamedLists() {
        // xmlName() trait lists
        TODO("not implemented")
    }

    @Test
    fun canSerializeClassWithNestedClassField() {
        val a = A(
            B(2)
        )
        val serializer = FormUrlSerializer()
        a.serialize(serializer)
        assertEquals("""b.v=2""", serializer.toByteArray().decodeToString())
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
}
