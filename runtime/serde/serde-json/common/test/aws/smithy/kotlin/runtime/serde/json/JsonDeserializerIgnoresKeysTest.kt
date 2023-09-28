/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SdkObjectDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.deserializeStruct
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonDeserializerIgnoresKeysTest {
    private val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("x"))
    private val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("y"))
    private val Z_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("z"))
    private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        trait(IgnoreKey("z"))
        field(X_DESCRIPTOR)
        field(Y_DESCRIPTOR)
        field(Z_DESCRIPTOR)
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
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    X_DESCRIPTOR.index -> x = deserializeInt()
                    Y_DESCRIPTOR.index -> y = deserializeInt()
                    Z_DESCRIPTOR.index -> z = deserializeInt()
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
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    X_DESCRIPTOR.index -> x = deserializeInt()
                    Y_DESCRIPTOR.index -> y = deserializeInt()
                    Z_DESCRIPTOR.index -> z = deserializeInt()
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
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    X_DESCRIPTOR.index -> x = deserializeInt()
                    Y_DESCRIPTOR.index -> y = deserializeInt()
                    Z_DESCRIPTOR.index -> z = deserializeInt()
                    null -> break@loop
                }
            }
        }

        assertEquals(1, x)
        assertEquals(2, y)
        assertEquals(null, z)
    }

    private val MISSING_KEYS_OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        trait(IgnoreKey("x"))
        field(Y_DESCRIPTOR)
    }

    @Test
    fun itIgnoresKeysNotInModel() {
        val payload = """
        {
            "y": 2,
            "x": 1
        }
        """.trimIndent().encodeToByteArray()

        val deserializer = JsonDeserializer(payload)
        var result: Int? = null
        deserializer.deserializeStruct(MISSING_KEYS_OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    Y_DESCRIPTOR.index -> result = deserializeInt()
                    null -> break@loop
                    else -> result = deserializeInt()
                }
            }
        }

        assertEquals(2, result)
    }

    private val W_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("w"))
    private val MULT_KEYS_OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        trait(IgnoreKey("w"))
        trait(IgnoreKey("z"))
        field(W_DESCRIPTOR)
        field(X_DESCRIPTOR)
        field(Y_DESCRIPTOR)
        field(Z_DESCRIPTOR)
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
        deserializer.deserializeStruct(MULT_KEYS_OBJ_DESCRIPTOR) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    W_DESCRIPTOR.index -> w = deserializeInt()
                    X_DESCRIPTOR.index -> x = deserializeInt()
                    Y_DESCRIPTOR.index -> y = deserializeInt()
                    Z_DESCRIPTOR.index -> z = deserializeInt()
                    null -> break@loop
                }
            }
        }

        assertEquals(null, w)
        assertEquals(1, x)
        assertEquals(2, y)
        assertEquals(null, z)
    }
}
