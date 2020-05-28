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
}
