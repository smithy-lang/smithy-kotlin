/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
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
    fun testByteStreamWriteToFile(): Unit = runBlocking {
        val content = "a lep is a ball\na tay is a hammer\na flix is a comb\na corf is a tiger".repeat(500)
        val source = object : ByteStream.SourceStream() {
            override fun readFrom(): SdkSource = content.encodeToByteArray().source()
        }

        val file = RandomTempFile(0)
        source.writeToFile(file)

        val actual = file.asByteStream().toByteArray()
        assertEquals(content.length.toLong(), file.length())
        assertEquals(content.length, actual.size)
        assertEquals(content, actual.decodeToString())
    }
}
