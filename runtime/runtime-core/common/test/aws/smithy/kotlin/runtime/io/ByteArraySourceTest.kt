/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteArraySourceTest {
    @Test
    fun testByteArraySource() {
        val contents = "12345678".encodeToByteArray()
        val source = contents.source()

        val sink = SdkBuffer()
        assertEquals(2L, source.read(sink, 2))
        assertEquals(4L, source.read(sink, 4))
        assertEquals(1L, source.read(sink, 1))
        assertEquals(1L, source.read(sink, 1))
        assertEquals(-1L, source.read(sink, 1))

        assertEquals(contents.decodeToString(), sink.readUtf8())

        val allAtOnceSource = contents.source()
        assertEquals(8L, allAtOnceSource.read(sink, Long.MAX_VALUE))
        assertEquals(contents.decodeToString(), sink.readUtf8())
    }
}
