/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.Headers as OkHttpHeaders

class OkHttpHeadersAdapterTest {
    @Test
    fun testOkHttpHeadersAdapter() {
        val okHeaders = OkHttpHeaders.Builder().apply {
            add("foo", "bar")
            add("Foo", "baz")
            add("bar", "qux")
        }.build()

        val actual = OkHttpHeadersAdapter(okHeaders)

        assertFalse(actual.isEmpty())
        assertTrue(actual.caseInsensitiveName)
        assertEquals(setOf("foo", "bar"), actual.names())
        assertEquals("bar", actual["foo"])
        assertEquals("bar", actual["fOO"])
        assertEquals(listOf("bar", "baz"), actual.getAll("foo"))
        assertEquals(listOf("bar", "baz"), actual.getAll("FoO"))

        assertEquals("qux", actual["bar"])
    }
}
