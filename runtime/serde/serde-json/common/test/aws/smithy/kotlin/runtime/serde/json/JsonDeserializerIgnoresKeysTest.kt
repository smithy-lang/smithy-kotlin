/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonDeserializerIgnoresKeysTest {
    class IgnoresKeysTest {
        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("x"))
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("y"))
            val Z_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("z"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(IgnoreKey("z")) // <----
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
                field(Z_DESCRIPTOR)
            }
        }
    }

    @Test
    fun itIgnoresKeys() {
        val payload = """
        {
            "x": 1,
            "y": 2,
            "z": 3
        }
        """.trimIndent().encodeToByteArray()

        val deserializer = JsonDeserializer(payload)
        var x: Int? = null
        var y: Int? = null
        var z: Int? = null
        deserializer.deserializeStruct(IgnoresKeysTest.OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    IgnoresKeysTest.X_DESCRIPTOR.index -> x = deserializeInt()
                    IgnoresKeysTest.Y_DESCRIPTOR.index -> y = deserializeInt()
                    IgnoresKeysTest.Z_DESCRIPTOR.index -> z = deserializeInt()
                    null -> break@loop
                }
            }
        }

        assertEquals(1, x)
        assertEquals(2, y)
        assertEquals(null, z)
    }

    @Test
    fun itIgnoresKeysOutOfOrder() {
        val payload = """
        {
            "z": 3,
            "x": 1,
            "y": 2
        }
        """.trimIndent().encodeToByteArray()

        val deserializer = JsonDeserializer(payload)
        var x: Int? = null
        var y: Int? = null
        var z: Int? = null
        deserializer.deserializeStruct(IgnoresKeysTest.OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    IgnoresKeysTest.X_DESCRIPTOR.index -> x = deserializeInt()
                    IgnoresKeysTest.Y_DESCRIPTOR.index -> y = deserializeInt()
                    IgnoresKeysTest.Z_DESCRIPTOR.index -> z = deserializeInt()
                    null -> break@loop
                }
            }
        }

        assertEquals(1, x)
        assertEquals(2, y)
        assertEquals(null, z)
    }

    @Test
    fun itIgnoresKeysManyTimes() {
        val payload = """
        {
            "x": 1,
            "y": 2,
            "z": 3,
            "z": 3,
            "z": 3
        }
        """.trimIndent().encodeToByteArray()

        val deserializer = JsonDeserializer(payload)
        var x: Int? = null
        var y: Int? = null
        var z: Int? = null
        deserializer.deserializeStruct(IgnoresKeysTest.OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    IgnoresKeysTest.X_DESCRIPTOR.index -> x = deserializeInt()
                    IgnoresKeysTest.Y_DESCRIPTOR.index -> y = deserializeInt()
                    IgnoresKeysTest.Z_DESCRIPTOR.index -> z = deserializeInt()
                    null -> break@loop
                }
            }
        }

        assertEquals(1, x)
        assertEquals(2, y)
        assertEquals(null, z)
    }

    class IgnoresMultipleKeysTest {
        companion object {
            val W_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("w"))
            val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("x"))
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("y"))
            val Z_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("z"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(IgnoreKey("w")) // <----
                trait(IgnoreKey("z")) // <----
                field(W_DESCRIPTOR)
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
                field(Z_DESCRIPTOR)
            }
        }
    }

    @Test
    fun itIgnoresMultipleKeys() {
        val payload = """
        {
            "w": 0,
            "x": 1,
            "y": 2,
            "z": 3
        }
        """.trimIndent().encodeToByteArray()

        val deserializer = JsonDeserializer(payload)
        var w: Int? = null
        var x: Int? = null
        var y: Int? = null
        var z: Int? = null
        deserializer.deserializeStruct(IgnoresMultipleKeysTest.OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    IgnoresMultipleKeysTest.W_DESCRIPTOR.index -> w = deserializeInt()
                    IgnoresMultipleKeysTest.X_DESCRIPTOR.index -> x = deserializeInt()
                    IgnoresMultipleKeysTest.Y_DESCRIPTOR.index -> y = deserializeInt()
                    IgnoresMultipleKeysTest.Z_DESCRIPTOR.index -> z = deserializeInt()
                    null -> break@loop
                }
            }
        }

        assertEquals(null, w)
        assertEquals(1, x)
        assertEquals(2, y)
        assertEquals(null, z)
    }

    // Now testing `regardlessOfInModel = false` option
    class IgnoresKeysConsideringModelTest {
        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("x"))
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("y"))
            val Z_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("z"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(IgnoreKey("x", false)) // <----
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
                field(Z_DESCRIPTOR)
            }
        }
    }

    @Test
    fun itDoesNotIgnoreKeyBecauseItWasInTheModel() {
        val payload = """
        {
            "x": 0
        }
        """.trimIndent().encodeToByteArray()

        val deserializer = JsonDeserializer(payload)
        var unionValue: Int? = null
        deserializer.deserializeStruct(IgnoresKeysConsideringModelTest.OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    IgnoresKeysConsideringModelTest.X_DESCRIPTOR.index -> unionValue = deserializeInt()
                    IgnoresKeysConsideringModelTest.Y_DESCRIPTOR.index -> unionValue = deserializeInt()
                    IgnoresKeysConsideringModelTest.Z_DESCRIPTOR.index -> unionValue = deserializeInt()
                    null -> break@loop
                    else -> unionValue = deserializeInt()
                }
            }
        }

        assertEquals(0, unionValue)
    }

    class IgnoresKeysBecauseNotInModelTest {
        companion object {
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("y"))
            val Z_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("z"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(IgnoreKey("x", false)) // <----
                field(Y_DESCRIPTOR)
                field(Z_DESCRIPTOR)
            }
        }
    }

    @Test
    fun itIgnoresKeyBecauseWasNotInTheModel() {
        val payload = """
        {
            "x": 0
        }
        """.trimIndent().encodeToByteArray()

        val deserializer = JsonDeserializer(payload)
        var unionValue: Int? = null
        deserializer.deserializeStruct(IgnoresKeysBecauseNotInModelTest.OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    IgnoresKeysBecauseNotInModelTest.Y_DESCRIPTOR.index -> unionValue = deserializeInt()
                    IgnoresKeysBecauseNotInModelTest.Z_DESCRIPTOR.index -> unionValue = deserializeInt()
                    null -> break@loop
                    else -> unionValue = deserializeInt()
                }
            }
        }

        assertEquals(null, unionValue)
    }
}
