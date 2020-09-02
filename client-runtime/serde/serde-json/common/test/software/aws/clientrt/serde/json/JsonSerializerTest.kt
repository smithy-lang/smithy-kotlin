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
package software.aws.clientrt.serde.json

import kotlin.test.Test
import kotlin.test.assertEquals
import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.SdkFieldDescriptor.Companion.ANONYMOUS_DESCRIPTOR

@OptIn(ExperimentalStdlibApi::class)
class JsonSerializerTest {

    @Test
    fun `can serialize class with class field`() {
        val a = A(
            B(2)
        )
        val json = JsonSerializer()
        a.serialize(json)
        assertEquals("""{"b":{"value":2}}""", json.toByteArray().decodeToString())
    }

    class A(private val b: B) : SdkSerializable {
        companion object {
            val descriptorB: SdkFieldDescriptor = SdkFieldDescriptor("b", SerialKind.Struct)
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(ANONYMOUS_DESCRIPTOR) {
                field(descriptorB, b)
            }
        }
    }

    data class B(private val value: Int) : SdkSerializable {
        companion object {
            val descriptorValue = SdkFieldDescriptor("value", SerialKind.Integer)
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct(ANONYMOUS_DESCRIPTOR) {
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
        val json = JsonSerializer()
        json.serializeList(ANONYMOUS_DESCRIPTOR) {
            for (value in obj) {
                value.serialize(json)
            }
        }
        assertEquals("""[{"value":1},{"value":2},{"value":3}]""", json.toByteArray().decodeToString())
    }

    @Test
    fun `can serialize map`() {
        val objs = mapOf("A1" to A(
            B(1)
        ), "A2" to A(
            B(
                2
            )
        ), "A3" to A(
            B(
                3
            )
        )
        )
        val json = JsonSerializer()
        json.serializeMap(ANONYMOUS_DESCRIPTOR) {
            for (obj in objs) {
                entry(obj.key, obj.value)
            }
        }
        assertEquals("""{"A1":{"b":{"value":1}},"A2":{"b":{"value":2}},"A3":{"b":{"value":3}}}""", json.toByteArray().decodeToString())
    }

    @Test
    fun `can serialize all primitives`() {
        val json = JsonSerializer()
        data.serialize(json)

        assertEquals("""{"boolean":true,"byte":10,"short":20,"int":30,"long":40,"float":50.0,"double":60.0,"char":"A","string":"Str0","listInt":[1,2,3]}""", json.toByteArray().decodeToString())
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
    val unitNullable: Unit?,
    val listInt: List<Int>
) : SdkSerializable {
    companion object {
        val descriptorUnit = SdkFieldDescriptor("unit", SerialKind.Unit)
        val descriptorBoolean = SdkFieldDescriptor("boolean", SerialKind.Boolean)
        val descriptorByte = SdkFieldDescriptor("byte", SerialKind.Byte)
        val descriptorShort = SdkFieldDescriptor("short", SerialKind.Short)
        val descriptorInt = SdkFieldDescriptor("int", SerialKind.Integer)
        val descriptorLong = SdkFieldDescriptor("long", SerialKind.Long)
        val descriptorFloat = SdkFieldDescriptor("float", SerialKind.Float)
        val descriptorDouble = SdkFieldDescriptor("double", SerialKind.Double)
        val descriptorChar = SdkFieldDescriptor("char", SerialKind.Char)
        val descriptorString = SdkFieldDescriptor("string", SerialKind.String)
        val descriptorUnitNullable = SdkFieldDescriptor("unitNullable", SerialKind.Unit)
        val descriptorListInt = SdkFieldDescriptor("listInt", SerialKind.List)
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(ANONYMOUS_DESCRIPTOR) {
            serializeNull(descriptorUnit)
            field(descriptorBoolean, boolean)
            field(descriptorByte, byte)
            field(descriptorShort, short)
            field(descriptorInt, int)
            field(descriptorLong, long)
            field(descriptorFloat, float)
            field(descriptorDouble, double)
            field(descriptorChar, char)
            field(descriptorString, string)
            serializeNull(descriptorUnitNullable)
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
    null, listOf(1, 2, 3)
)
