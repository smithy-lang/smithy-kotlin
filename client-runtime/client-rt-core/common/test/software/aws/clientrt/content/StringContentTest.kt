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

class StringContentTest {
    @Test
    fun `it can be consumed as ByteStream`() {
        val content = StringContent("testing")
        assertEquals(7, content.contentLength)
        val expected = byteArrayOf(0x74, 0x65, 0x73, 0x74, 0x69, 0x6e, 0x67)
        val actual = content.bytes()
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], "index: $i")
        }

        // test companion object
        val stream = ByteStream.fromString("testing")
        assertTrue(stream is ByteStream.Buffer)
    }

    @Test
    fun `it handles UTF-8`() {
        val content = StringContent("你好")
        content.bytes().forEach { print("$it ") }
        assertEquals(6, content.contentLength)
        val expected = byteArrayOf(-28, -67, -96, -27, -91, -67)
        val actual = content.bytes()
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], "index: $i")
        }
    }
}
