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
