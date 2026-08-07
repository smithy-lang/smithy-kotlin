/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ShapeIdTest {
    @Test
    fun testParsesShapeId() {
        val id = ShapeId.from("com.example#Bird")
        assertEquals("com.example", id.namespace)
        assertEquals("Bird", id.name)
        assertNull(id.member)
        assertEquals("com.example#Bird", id.absoluteId)
    }

    @Test
    fun testParsesMemberShapeId() {
        val id = ShapeId.from("com.example#Bird\$name")
        assertEquals("com.example", id.namespace)
        assertEquals("Bird", id.name)
        assertEquals("name", id.member)
        assertEquals("com.example#Bird\$name", id.absoluteId)
    }

    @Test
    fun testWithMember() {
        val id = ShapeId.from("com.example#Bird").withMember("colors")
        assertEquals("colors", id.member)
        assertEquals("com.example#Bird\$colors", id.absoluteId)
    }

    @Test
    fun testEqualityAndHashCode() {
        val a = ShapeId.from("com.example#Bird")
        val b = ShapeId.from("com.example", "Bird")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testRejectsMalformedId() {
        assertFailsWith<IllegalArgumentException> { ShapeId.from("no-hash") }
        assertFailsWith<IllegalArgumentException> { ShapeId.from("#Bird") }
        assertFailsWith<IllegalArgumentException> { ShapeId.from("com.example#") }
    }
}
