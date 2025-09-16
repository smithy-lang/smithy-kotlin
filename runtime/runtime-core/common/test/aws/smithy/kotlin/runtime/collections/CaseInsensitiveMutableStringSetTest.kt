/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.collections

import kotlin.test.*

private val input = setOf("APPLE", "banana", "cHeRrY")
private val variations = (input + input.map { it.lowercase() } + input.map { it.uppercase() })
private val disjoint = setOf("durIAN", "ELdeRBerRY", "FiG")
private val subset = input - "APPLE"
private val intersecting = subset + disjoint

class CaseInsensitiveMutableStringSetTest {
    private fun assertSize(size: Int, set: CaseInsensitiveMutableStringSet) {
        assertEquals(size, set.size)
        val emptyAsserter: (Boolean) -> Unit =
            if (size == 0) {
                ::assertTrue
            } else {
                ::assertFalse
            }
        emptyAsserter(set.isEmpty())
    }

    @Test
    fun testInitialization() {
        val set = CaseInsensitiveMutableStringSet(input)
        assertSize(3, set)
    }

    @Test
    fun testAdd() {
        val set = CaseInsensitiveMutableStringSet(input)
        set += "durIAN"
        assertSize(4, set)
    }

    @Test
    fun testAddAll() {
        val set = CaseInsensitiveMutableStringSet(input)
        assertFalse(set.addAll(set))

        val intersecting = input + "durian"
        assertTrue(set.addAll(intersecting))
        assertSize(4, set)
    }

    @Test
    fun testClear() {
        val set = CaseInsensitiveMutableStringSet(input)
        set.clear()
        assertSize(0, set)
    }

    @Test
    fun testContains() {
        val set = CaseInsensitiveMutableStringSet(input)
        variations.forEach { assertTrue("Set should contain element $it") { it in set } }

        assertFalse("durian" in set)
    }

    @Test
    fun testContainsAll() {
        val set = CaseInsensitiveMutableStringSet(input)
        assertTrue(set.containsAll(variations))

        val intersecting = input + "durian"
        assertFalse(set.containsAll(intersecting))
    }

    @Test
    fun testEquality() {
        val left = CaseInsensitiveMutableStringSet(input)
        val right = CaseInsensitiveMutableStringSet(input)
        assertEquals(left, right)

        left -= "apple"
        assertNotEquals(left, right)

        right -= "ApPlE"
        assertEquals(left, right)
    }

    @Test
    fun testIterator() {
        val set = CaseInsensitiveMutableStringSet(input)
        val iterator = set.iterator()

        assertTrue(iterator.hasNext())
        assertEquals("apple", iterator.next())

        assertTrue(iterator.hasNext())
        assertEquals("banana", iterator.next())
        iterator.remove()
        assertSize(2, set)

        assertTrue(iterator.hasNext())
        assertEquals("cherry", iterator.next())

        assertFalse(iterator.hasNext())
        assertTrue(set.containsAll(input - "banana"))
    }

    @Test
    fun testRemove() {
        val set = CaseInsensitiveMutableStringSet(input)
        set -= "BANANA"
        assertSize(2, set)
    }

    @Test
    fun testRemoveAll() {
        val set = CaseInsensitiveMutableStringSet(input)
        assertFalse(set.removeAll(disjoint))

        assertTrue(set.removeAll(intersecting))
        assertSize(1, set)
        assertTrue("apple" in set)
    }

    @Test
    fun testRetainAll() {
        val set = CaseInsensitiveMutableStringSet(input)
        assertFalse(set.retainAll(set))
        assertSize(3, set)

        assertTrue(set.retainAll(intersecting))
        assertSize(2, set)
        assertTrue(set.containsAll(subset))
    }
}
