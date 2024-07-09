/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CborDeserializerTest {
    @Test
    fun testNumberDeserializationThrowsOnOutOfRange() {
        val serializer = CborSerializer()
        serializer.serializeLong(Long.MAX_VALUE)
        serializer.serializeLong(Long.MAX_VALUE)
        serializer.serializeLong(Long.MAX_VALUE)
        serializer.serializeLong(Long.MAX_VALUE)
        serializer.serializeLong(Long.MAX_VALUE)

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }

        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails { deserializer.deserializeInt() }
        assertFails { deserializer.deserializeShort() }
        assertFails { deserializer.deserializeByte() }
        assertFails { deserializer.deserializeByte() }
        assertEquals(Long.MAX_VALUE, deserializer.deserializeLong())
    }
}
