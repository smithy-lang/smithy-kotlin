/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import kotlin.test.*

class HttpStatusCodeTest {
    @Test
    fun isSuccess() {
        assertTrue(HttpStatusCode.OK.isSuccess())
        assertTrue(HttpStatusCode(299, "").isSuccess())
        assertFalse(HttpStatusCode(300, "").isSuccess())
    }

    @Test
    fun fromValue() {
        assertEquals(HttpStatusCode.OK, HttpStatusCode.fromValue(200))
        assertEquals(HttpStatusCode(3001, "").value, HttpStatusCode.fromValue(3001).value)
    }

    @Test
    fun itCanMatchCategories() {
        assertEquals(HttpStatusCode.OK.category(), HttpStatusCode.Accepted.category())
        assertNotEquals(HttpStatusCode.NotFound.category(), HttpStatusCode.BadGateway.category())
    }

    @Test
    fun itFailsWithInvalidHttpCodes() {
        assertFailsWith<IllegalStateException>("Invalid HTTP code 999") {
            HttpStatusCode.Category.fromCode(999)
        }
    }
}
