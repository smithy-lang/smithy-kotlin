/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import kotlin.test.*

class HttpStatusCodeTest {
    @Test
    fun `is success`() {
        assertTrue(HttpStatusCode.OK.isSuccess())
        assertTrue(HttpStatusCode(299, "").isSuccess())
        assertFalse(HttpStatusCode(300, "").isSuccess())
    }

    @Test
    fun `from value`() {
        assertEquals(HttpStatusCode.OK, HttpStatusCode.fromValue(200))
        assertEquals(HttpStatusCode(3001, "").value, HttpStatusCode.fromValue(3001).value)
    }

    @Test
    fun `it can match categories`() {
        assertEquals(HttpStatusCode.OK.category(), HttpStatusCode.Accepted.category())
        assertNotEquals(HttpStatusCode.NotFound.category(), HttpStatusCode.BadGateway.category())
    }

    @Test
    fun `it fails with invalid http codes`() {
        assertFailsWith<IllegalStateException>("Invalid HTTP code 999") {
            HttpStatusCode.Category.fromCode(999)
        }
    }
}
