/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeadersTest {

    @Test
    fun itBuilds() {
        val actual = Headers {
            append("key1", "v1")
            appendAll("key2", listOf("v2", "v3"))
        }

        assertEquals("v1", actual["key1"])
        assertEquals(listOf("v2", "v3"), actual.getAll("key2"))
        assertTrue(actual.names().containsAll(listOf("key1", "key2")))
        assertFalse(actual.isEmpty())

        val actual2 = Headers {
            append("key", "value")
        }
        assertEquals("Headers [key=[value]]", "$actual2")
    }

    @Test
    fun testSubsequentModificationsDontAffectOriginal() {
        val builder = HeadersBuilder()

        builder.append("a", "alligator")
        builder.append("b", "bunny")
        builder.append("c", "chinchilla")
        val first = builder.build()
        val firstExpected = mapOf(
            "a" to listOf("alligator"),
            "b" to listOf("bunny"),
            "c" to listOf("chinchilla"),
        )

        builder.append("a", "anteater")
        builder.remove("b")
        builder["c"] = "crocodile"
        val second = builder.build()
        val secondExpected = mapOf(
            "a" to listOf("alligator", "anteater"),
            "c" to listOf("crocodile"),
        )

        assertEquals(firstExpected.entries, first.entries())
        assertEquals(secondExpected.entries, second.entries())
    }
}
