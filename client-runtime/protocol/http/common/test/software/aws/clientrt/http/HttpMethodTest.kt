/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class HttpMethodTest {
    @Test
    fun `string representation`() {
        assertEquals("GET", HttpMethod.GET.name)
        assertEquals("POST", HttpMethod.POST.toString())
    }

    @Test
    fun `it parses`() {
        assertEquals(
            HttpMethod.GET,
            HttpMethod.parse("get")
        )
        assertEquals(
            HttpMethod.POST,
            HttpMethod.parse("pOst")
        )
        assertEquals(
            HttpMethod.PATCH,
            HttpMethod.parse("PATCH")
        )
        assertEquals(
            HttpMethod.PUT,
            HttpMethod.parse("Put")
        )
        assertEquals(
            HttpMethod.DELETE,
            HttpMethod.parse("delete")
        )
        assertEquals(
            HttpMethod.OPTIONS,
            HttpMethod.parse("OPTIONS")
        )
        assertFails {
            HttpMethod.parse("my method")
        }
    }
}
