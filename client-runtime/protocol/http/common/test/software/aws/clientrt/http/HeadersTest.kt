/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeadersTest {

    @Test
    fun `it builds`() {
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
}
