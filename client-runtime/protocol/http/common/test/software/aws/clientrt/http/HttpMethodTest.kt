/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
