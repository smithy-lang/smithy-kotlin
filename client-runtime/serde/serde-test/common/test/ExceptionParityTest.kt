/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.json.JsonSerdeProvider
import software.aws.clientrt.serde.xml.XmlSerdeProvider
import software.aws.clientrt.serde.xml.XmlSerialName
import software.aws.clientrt.testing.runSuspendTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ExceptionParityTest {
    companion object {
        private val xmlProvider = XmlSerdeProvider()
        private val jsonProvider = JsonSerdeProvider()
    }

    private fun parityTest(json: String, xml: String, expected: Any) = runSuspendTest {
        suspend fun subtest(value: String, provider: SerdeProvider) = when (expected) {
            is KClass<*> -> {
                val ex = assertFails { value.deserialize(provider) }
                assertEquals(expected, ex::class, "Found exception $ex")
            }
            else -> {
                val actual = value.deserialize(provider)
                assertEquals(expected, actual)
            }
        }

        subtest(json, jsonProvider)
        subtest(xml, xmlProvider)
    }

    @Test
    fun `it should pass a sanity test without exceptions`() = parityTest(
        json = """ { "id": 123, "name": "Alice" } """,
        xml = """ <employee> <id>123</id> <name>Alice</name> </employee> """,
        expected = Employee(123, "Alice"),
    )

    @Test
    fun `it should throw uniform exceptions for type errors`() = parityTest(
        json = """ { "id" : "Alice", "name": "Alice" } """, // ID given as string
        xml = """ <employee> <id>Alice</id> <name>Alice</name> </employee> """, // ID given as string
        expected = DeserializationException::class,
    )
}

data class Employee(val id: Int?, val name: String?) {
    companion object {
        private val ID_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, "id".toSerialNames())
        private val NAME_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, "name".toSerialNames())
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("employee"))
            field(ID_DESCRIPTOR)
            field(NAME_DESCRIPTOR)
        }

        suspend fun deserialize(deserializer: Deserializer): Employee {
            var id: Int? = null
            var name: String? = null
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@ while (true) {
                    when (findNextFieldIndex()) {
                        ID_DESCRIPTOR.index -> id = deserializeInt()
                        NAME_DESCRIPTOR.index -> name = deserializeString()
                        null -> break@loop
                        else -> throw IllegalStateException("Unexpected field in deserializer")
                    }
                }
            }
            return Employee(id, name)
        }
    }
}

private suspend fun String.deserialize(withProvider: SerdeProvider): Employee {
    val deserializer = withProvider.deserializer(this.encodeToByteArray())
    return Employee.deserialize(deserializer)
}
