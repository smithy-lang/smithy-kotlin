/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmesPathTest {
    @Test
    fun flattenNestedLists() {
        val nestedList = listOf(
            listOf(1, 2, 3),
            listOf(4, 5),
            listOf(6),
        )
        val flattenedList = nestedList.flattenIfPossible()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), flattenedList)
    }

    @Test
    fun flattenEmptyNestedLists() {
        val nestedList = listOf(
            listOf<Int>(),
            listOf(),
            listOf(),
        )
        val flattenedList = nestedList.flattenIfPossible()
        assertTrue(flattenedList.isEmpty())
    }

    @Test
    fun flattenNestedEmptyAndNonEmptyNestedLists() {
        val nestedList = listOf(
            listOf(1, 2),
            listOf(),
            listOf(3, 4, 5),
        )
        val flattenedList = nestedList.flattenIfPossible()
        assertEquals(listOf(1, 2, 3, 4, 5), flattenedList)
    }

    @Test
    fun flattenList() {
        val nestedList = listOf(
            listOf(1, 2, 3),
        )
        val flattenedList = nestedList.flattenIfPossible()
        assertEquals(listOf(1, 2, 3), flattenedList)
    }
}
