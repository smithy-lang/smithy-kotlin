/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StringValuesMapTest {
    @Test
    fun testEmptyEquality() {
        assertEquals(StringValuesMapBuilder().build(), StringValuesMapBuilder().build())
    }

    @Test
    fun testEquality() {
        assertEquals(
            StringValuesMapBuilder().apply {
                append("k", "v")
                appendAll("i", listOf("j", "k"))
            }.build(),
            StringValuesMapBuilder().apply {
                append("k", "v")
                appendAll("i", listOf("j", "k"))
            }.build(),
        )
    }

    @Test
    fun testInequality() {
        assertNotEquals(
            StringValuesMapBuilder().apply {
                append("k", "v")
            }.build(),
            StringValuesMapBuilder().apply {
                append("k", "v")
                appendAll("i", listOf("j", "k"))
            }.build(),
        )
        assertNotEquals(
            StringValuesMapBuilder().apply {
                append("k", "v")
            }.build(),
            StringValuesMapBuilder().apply {
                append("k", "v2")
            }.build(),
        )
        assertNotEquals(
            StringValuesMapBuilder().apply {
                append("k", "v")
            }.build(),
            StringValuesMapBuilder().apply {
                append("K", "v")
            }.build(),
        )
    }

    @Test
    fun testCaseInsensitiveEquality() {
        val i = StringValuesMapBuilder(caseInsensitiveName = true).apply {
            append("k", "v")
        }.build()
        val j = StringValuesMapBuilder(caseInsensitiveName = true).apply {
            append("K", "v")
        }.build()

        assertEquals(i, j)
        assertEquals(j, i)
    }

    @Test
    fun testCaseInsensitiveInequality() {
        val i = StringValuesMapBuilder(caseInsensitiveName = true).apply {
            append("k", "v")
        }.build()
        val j = StringValuesMapBuilder(caseInsensitiveName = true).apply {
            append("K", "v2")
        }.build()

        assertNotEquals(i, j)
        assertNotEquals(j, i)
    }

    @Test
    fun testCrossCaseSensitiveInequality() {
        val i = StringValuesMapBuilder(caseInsensitiveName = true).apply {
            append("k", "v")
        }.build()
        val j = StringValuesMapBuilder(caseInsensitiveName = false).apply {
            append("k", "v")
        }.build()

        assertNotEquals(i, j)
        assertNotEquals(j, i)
    }
}
