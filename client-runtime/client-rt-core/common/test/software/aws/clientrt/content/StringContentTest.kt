/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
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
