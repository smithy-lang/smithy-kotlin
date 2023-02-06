/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ValuesMapTest {
    @Test
    fun testEmptyEquality() {
        assertEquals(ValuesMapBuilder<String>().build(), ValuesMapBuilder<String>().build())
    }

    @Test
    fun testEquality() {
        assertEquals(
            ValuesMapBuilder<String>().apply {
                append("k", "v")
                appendAll("i", listOf("j", "k"))
            }.build(),
            ValuesMapBuilder<String>().apply {
                append("k", "v")
                appendAll("i", listOf("j", "k"))
            }.build(),
        )
    }

    @Test
    fun testInequality() {
        assertNotEquals(
            ValuesMapBuilder<String>().apply {
                append("k", "v")
            }.build(),
            ValuesMapBuilder<String>().apply {
                append("k", "v")
                appendAll("i", listOf("j", "k"))
            }.build(),
        )
        assertNotEquals(
            ValuesMapBuilder<String>().apply {
                append("k", "v")
            }.build(),
            ValuesMapBuilder<String>().apply {
                append("k", "v2")
            }.build(),
        )
        assertNotEquals(
            ValuesMapBuilder<String>().apply {
                append("k", "v")
            }.build(),
            ValuesMapBuilder<String>().apply {
                append("K", "v")
            }.build(),
        )
    }

    @Test
    fun testCaseInsensitiveEquality() {
        val i = ValuesMapBuilder<String>(caseInsensitiveName = true).apply {
            append("k", "v")
        }.build()
        val j = ValuesMapBuilder<String>(caseInsensitiveName = true).apply {
            append("K", "v")
        }.build()

        assertEquals(i, j)
        assertEquals(j, i)
    }

    @Test
    fun testCaseInsensitiveInequality() {
        val i = ValuesMapBuilder<String>(caseInsensitiveName = true).apply {
            append("k", "v")
        }.build()
        val j = ValuesMapBuilder<String>(caseInsensitiveName = true).apply {
            append("K", "v2")
        }.build()

        assertNotEquals(i, j)
        assertNotEquals(j, i)
    }

    @Test
    fun testCrossCaseSensitiveInequality() {
        val i = ValuesMapBuilder<String>(caseInsensitiveName = true).apply {
            append("k", "v")
        }.build()
        val j = ValuesMapBuilder<String>(caseInsensitiveName = false).apply {
            append("k", "v")
        }.build()

        assertNotEquals(i, j)
        assertNotEquals(j, i)
    }
}
