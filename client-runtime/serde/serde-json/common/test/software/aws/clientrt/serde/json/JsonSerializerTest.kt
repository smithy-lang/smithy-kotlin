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
            val descriptorB: SdkFieldDescriptor = SdkFieldDescriptor("b")
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct {
                field(descriptorB, b)
            }
        }
    }

    data class B(private val value: Int) : SdkSerializable {
        companion object {
            val descriptorValue = SdkFieldDescriptor("value")
        }

        override fun serialize(serializer: Serializer) {
            serializer.serializeStruct {
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
        json.serializeList {
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
        json.serializeMap {
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
        val descriptorUnit = SdkFieldDescriptor("unit")
        val descriptorBoolean = SdkFieldDescriptor("boolean")
        val descriptorByte = SdkFieldDescriptor("byte")
        val descriptorShort = SdkFieldDescriptor("short")
        val descriptorInt = SdkFieldDescriptor("int")
        val descriptorLong = SdkFieldDescriptor("long")
        val descriptorFloat = SdkFieldDescriptor("float")
        val descriptorDouble = SdkFieldDescriptor("double")
        val descriptorChar = SdkFieldDescriptor("char")
        val descriptorString = SdkFieldDescriptor("string")
        val descriptorUnitNullable = SdkFieldDescriptor("unitNullable")
        val descriptorListInt = SdkFieldDescriptor("listInt")
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct {
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
