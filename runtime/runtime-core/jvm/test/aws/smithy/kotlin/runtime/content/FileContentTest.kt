/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.testing.RandomTempFile
import kotlinx.coroutines.runBlocking
import kotlin.test.*

// something larger than internal buffer size
private const val TEST_FILE_SIZE: Long = 1024 * 32

class FileContentTest {

    @Test
    fun testReadWholeFile(): Unit = runBlocking {
        val file = RandomTempFile(TEST_FILE_SIZE)
        val expected = file.readBytes()
        val contents = file.asByteStream().toByteArray()

        assertContentEquals(expected, contents)
    }

    @Test
    fun testReadPartial(): Unit = runBlocking {
        val file = RandomTempFile(TEST_FILE_SIZE)
        val expected = file.readBytes().sliceArray(15..72)
        val contents = file.asByteStream(15, 72).toByteArray()
        assertContentEquals(expected, contents)
    }

    @Test
    fun testChannelCancellation(): Unit = runBlocking {
        val file = RandomTempFile(TEST_FILE_SIZE)
        val fc = FileContent(file)
        val ch = fc.newReader()

        val sink = SdkBuffer()
        assertEquals(1L, ch.read(sink, 1L))

        ch.cancel(null)
        assertTrue(ch.isClosedForRead)
        assertTrue(ch.isClosedForWrite)
    }
}
