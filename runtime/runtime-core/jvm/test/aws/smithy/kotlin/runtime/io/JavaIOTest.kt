/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaIOTest {
    @Test
    fun testInputStreamSource() {
        val content = "a flix is a comb"
        val istream = ByteArrayInputStream(content.encodeToByteArray())
        val sink = SdkBuffer()
        val rc = istream.source().use {
            it.buffer().readAll(sink)
        }

        assertEquals(content.length.toLong(), rc)
        assertEquals(content, sink.readUtf8())
    }

    @Test
    fun testOutputStreamSink() {
        val content = "a tay is a hammer"
        val ostream = ByteArrayOutputStream()

        val source = SdkBuffer().apply { writeUtf8(content) }
        ostream.sink().use {
            it.write(source, source.size)
        }

        assertEquals(content.length, ostream.size())
        assertEquals(content, ostream.toString())
    }
}
