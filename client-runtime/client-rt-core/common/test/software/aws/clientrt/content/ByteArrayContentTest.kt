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
package software.aws.clientrt.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByteArrayContentTest {
    @Test
    fun `it can be consumed as ByteStream`() {
        val actual = byteArrayOf(0x01, 0x02, 0x03)
        val content = ByteArrayContent(actual)
        assertEquals(3, content.contentLength)
        assertEquals(0x02, content.bytes()[1])

        // test companion object
        val stream = ByteStream.fromBytes(actual)
        assertTrue(stream is ByteStream.Buffer)
    }
}
