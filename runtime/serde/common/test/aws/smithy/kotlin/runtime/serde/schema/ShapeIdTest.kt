/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class ShapeIdTest {
    @Test
    fun testConstructsShapeId() {
        val id = shapeId("com.example", "Bird")
        assertEquals("com.example", id.namespace)
        assertEquals("Bird", id.name)
        assertEquals("com.example#Bird", id.absoluteId)
    }

    @Test
    fun testParsesShapeId() {
        val id = shapeId("com.example#Bird")
        assertEquals("com.example", id.namespace)
        assertEquals("Bird", id.name)
        assertEquals("com.example#Bird", id.absoluteId)
    }

    @Test
    fun testParsesMemberShapeId() {
        val id = shapeId("com.example#Bird\$name")
        val member = assertIs<MemberShapeId>(id)
        assertEquals("com.example", member.namespace)
        assertEquals("Bird", member.name)
        assertEquals("name", member.member)
        assertEquals("com.example#Bird\$name", member.absoluteId)
    }

    @Test
    fun testWithMember() {
        val id = shapeId("com.example", "Bird").withMember("colors")
        assertEquals("colors", id.member)
        assertEquals("com.example#Bird\$colors", id.absoluteId)
    }

    @Test
    fun testWithMemberReplacesExisting() {
        val id = shapeId("com.example#Bird\$name").withMember("colors")
        assertEquals("colors", id.member)
        assertEquals("com.example#Bird\$colors", id.absoluteId)
    }

    @Test
    fun testEquality() {
        assertEquals(shapeId("com.example#Bird"), shapeId("com.example", "Bird"))
        assertEquals(
            shapeId("com.example#Bird\$name"),
            shapeId("com.example", "Bird").withMember("name"),
        )
        // a plain shape id and a member id never compare equal
        assertNotEquals<ShapeId>(shapeId("com.example#Bird"), shapeId("com.example#Bird\$name"))
    }

    @Test
    fun testRejectsMalformedId() {
        assertFailsWith<IllegalArgumentException> { shapeId("no-hash") }
        assertFailsWith<IllegalArgumentException> { shapeId("#Bird") }
        assertFailsWith<IllegalArgumentException> { shapeId("com.example#") }
        assertFailsWith<IllegalArgumentException> { shapeId("com.example#Bird\$") }
        assertFailsWith<IllegalArgumentException> { shapeId("", "Bird") }
    }
}
