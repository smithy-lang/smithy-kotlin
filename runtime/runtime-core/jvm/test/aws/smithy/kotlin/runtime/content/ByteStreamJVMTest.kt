/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.testing.RandomTempFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ByteStreamJVMTest {
    @Test
    fun `file as byte stream validates start`() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(-1)
        }
        assertEquals("start index -1 cannot be negative", e.message)
    }

    @Test
    fun `file as byte stream validates end`() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(endInclusive = 1024)
        }
        assertEquals("end index 1024 must be less than file size 1024", e.message)
    }

    @Test
    fun `file as byte stream validates start and end`() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(5, 1)
        }
        assertEquals("end index 1 must be greater than or equal to start index 5", e.message)
    }

    @Test
    fun `file as byte stream has contentLength`() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream()

        assertEquals(1024, stream.contentLength)
    }

    @Test
    fun `partial file as byte stream has contentLength`() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream(1, 1023)

        assertEquals(1023, stream.contentLength)
    }

    @Test
    fun `partial file as byte stream has contentLength with implicit end`() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream(1)

        assertEquals(1023, stream.contentLength)
    }

    @Test
    fun `file as byte stream matches read`() = runTest {
        val file = RandomTempFile(1024)

        val expected = file.readBytes()
        val actual = file.asByteStream().toByteArray()

        assertContentEquals(expected, actual)
    }

    @Test
    fun `partial file as byte stream matches read`() = runTest {
        val file = RandomTempFile(1024)

        val expected = file.readBytes()
        val part0 = file.asByteStream(endInclusive = 255).toByteArray()
        val part1 = file.asByteStream(256, 511).toByteArray()
        val part2 = file.asByteStream(512).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }

    @Test
    fun `partial file as byte stream using range`() = runTest {
        val file = RandomTempFile(1024)

        val expected = file.readBytes()
        val part0 = file.asByteStream(0L..255L).toByteArray()
        val part1 = file.asByteStream(256L..511L).toByteArray()
        val part2 = file.asByteStream(512L until file.length()).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }

    @Test
    fun `partial path as byte stream`() = runTest {
        val file = RandomTempFile(1024)
        val path = file.toPath()

        val expected = file.readBytes()
        val part0 = path.asByteStream(endInclusive = 255).toByteArray()
        val part1 = path.asByteStream(256, 511).toByteArray()
        val part2 = path.asByteStream(512).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }

    @Test
    fun `partial path as byte stream using range`() = runTest {
        val file = RandomTempFile(1024)
        val path = file.toPath()

        val expected = file.readBytes()
        val part0 = path.asByteStream(0L..255L).toByteArray()
        val part1 = path.asByteStream(256L..511L).toByteArray()
        val part2 = path.asByteStream(512L until file.length()).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }

    @Test
    fun `path as byte stream has contentLength`() = runTest {
        val path = RandomTempFile(1024).toPath()
        val stream = path.asByteStream()

        assertEquals(1024, stream.contentLength)
    }

    @Test
    fun `can create byte stream from empty file`() = runTest {
        val file = Files.createTempFile(null, null)
        val byteStream = file.asByteStream()
        assertEquals(0, byteStream.contentLength)
    }
}
