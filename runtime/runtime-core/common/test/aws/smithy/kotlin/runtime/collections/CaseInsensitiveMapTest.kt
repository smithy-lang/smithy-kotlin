/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaseInsensitiveMapTest {
    @Test
    fun mapOperationsIgnoreCase() {
        val map = CaseInsensitiveMap<String>()

        map["conTent-Type"] = "json"
        assertEquals("json", map["conTent-Type"])
        assertEquals("json", map["Content-Type"])
        assertEquals("json", map["content-type"])
        assertEquals("json", map["CONTENT-TYPE"])
    }

    @Test
    fun testContains() {
        val map = CaseInsensitiveMap<String>()
        map["A"] = "apple"
        map["B"] = "banana"
        map["C"] = "cherry"

        assertTrue("C" in map)
        assertTrue("c" in map)
        assertFalse("D" in map)
    }

    @Test
    fun testKeysContains() {
        val map = CaseInsensitiveMap<String>()
        map["A"] = "apple"
        map["B"] = "banana"
        map["C"] = "cherry"
        val keys = map.keys

        assertTrue("C" in keys)
        assertTrue("c" in keys)
        assertFalse("D" in keys)
    }

    @Test
    fun testEquality() {
        val left = CaseInsensitiveMap<String>()
        left["A"] = "apple"
        left["B"] = "banana"
        left["C"] = "cherry"

        val right = CaseInsensitiveMap<String>()
        right["c"] = "cherry"
        right["b"] = "banana"
        right["a"] = "apple"

        assertEquals(left, right)
    }

    @Test
    fun testEntriesEquality() {
        val left = CaseInsensitiveMap<String>()
        left["A"] = "apple"
        left["B"] = "banana"
        left["C"] = "cherry"

        val right = CaseInsensitiveMap<String>()
        right["c"] = "cherry"
        right["b"] = "banana"
        right["a"] = "apple"

        assertEquals(left.entries, right.entries)
    }

    @Test
    fun testEntriesEqualityWithNormalMap() {
        val left = CaseInsensitiveMap<String>()
        left["A"] = "apple"
        left["B"] = "banana"
        left["C"] = "cherry"

        val right = mutableMapOf(
            "c" to "cherry",
            "b" to "banana",
            "a" to "apple",
        )

        assertEquals(left.entries, right.entries)
    }

    @Test
    fun testToString() {
        val map = CaseInsensitiveMap<String>()
        map["A"] = "apple"
        map["B"] = "banana"
        map["C"] = "cherry"
        assertEquals("{A=apple, B=banana, C=cherry}", map.toString())
    }
}
