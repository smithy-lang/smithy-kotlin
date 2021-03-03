/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.content

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteArrayContentTest {
    @Test
    fun testByteArrayContent() {
        val raw = "foo"
        val content = ByteArrayContent(raw.encodeToByteArray())
        assertEquals(raw.length.toLong(), content.contentLength)
        assertEquals(raw, content.bytes().decodeToString())
    }
}
