/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io
import aws.smithy.kotlin.runtime.testing.RandomTempFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSinkTest {

    @Test
    fun testFileSink() {
        val expected = "a lep is a ball"
        val buffer = SdkBuffer().apply { writeUtf8(expected) }
        val file = RandomTempFile(0)
        val sink = file.sink()

        val rc = buffer.readAll(sink)
        assertEquals(expected.length.toLong(), rc)

        val actual = file.readText()
        assertEquals(expected, actual)
    }

    @Test
    fun testPathSink() {
        val expected = "a tay is a hammer"
        val buffer = SdkBuffer().apply { writeUtf8(expected) }
        val path = RandomTempFile(0).toPath()
        val sink = path.sink()

        val rc = buffer.readAll(sink)
        assertEquals(expected.length.toLong(), rc)

        val actual = path.readText()
        assertEquals(expected, actual)
    }
}
