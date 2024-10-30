/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.testing.RandomTempFile
import jdk.jshell.Snippet.Status
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.*

private val binaryData = ByteArray(1024) { it.toByte() }

class ByteStreamJVMTest {
    @Test
    fun testFileAsByteStreamValidatesStart() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(-1)
        }
        assertEquals("start index -1 cannot be negative", e.message)
    }

    @Test
    fun testFileAsAByteStreamValidatesEnd() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(endInclusive = 1024)
        }
        assertEquals("end index 1024 must be less than file size 1024", e.message)
    }

    @Test
    fun testFileAsByteStreamValidatesStartAndEnd() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(5, 1)
        }
        assertEquals("end index 1 must be greater than or equal to start index minus one (4)", e.message)
    }

    @Test
    fun testFileAsByteStreamHasContentLength() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream()

        assertEquals(1024, stream.contentLength)
    }

    @Test
    fun testPartialFileAsByteStreamHasContentLength() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream(1, 1023)

        assertEquals(1023, stream.contentLength)
    }

    @Test
    fun testPartialFileAsByteStreamHasImplicitEnd() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream(1)

        assertEquals(1023, stream.contentLength)
    }

    @Test
    fun testFileAsByteStreamRead() = runTest {
        val file = RandomTempFile(1024)

        val expected = file.readBytes()
        val actual = file.asByteStream().toByteArray()

        assertContentEquals(expected, actual)
    }

    @Test
    fun testPartialFileAsByteStreamRead() = runTest {
        val file = RandomTempFile(1024)

        val expected = file.readBytes()
        val part0 = file.asByteStream(endInclusive = 255).toByteArray()
        val part1 = file.asByteStream(256, 511).toByteArray()
        val part2 = file.asByteStream(512).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }

    @Test
    fun testPartialFileRangeAsByteStreamRead() = runTest {
        val file = RandomTempFile(1024)

        val expected = file.readBytes()
        val part0 = file.asByteStream(0L..255L).toByteArray()
        val part1 = file.asByteStream(256L..511L).toByteArray()
        val part2 = file.asByteStream(512L until file.length()).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }

    @Test
    fun testPartialPathAsByteStreamRead() = runTest {
        val file = RandomTempFile(1024)
        val path = file.toPath()

        val expected = file.readBytes()
        val part0 = path.asByteStream(endInclusive = 255).toByteArray()
        val part1 = path.asByteStream(256, 511).toByteArray()
        val part2 = path.asByteStream(512).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }

    @Test
    fun testPartialPathRangeAsByteStreamRead() = runTest {
        val file = RandomTempFile(1024)
        val path = file.toPath()

        val expected = file.readBytes()
        val part0 = path.asByteStream(0L..255L).toByteArray()
        val part1 = path.asByteStream(256L..511L).toByteArray()
        val part2 = path.asByteStream(512L until file.length()).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }

    @Test
    fun testPathAsByteStreamHasContentLength() = runTest {
        val path = RandomTempFile(1024).toPath()
        val stream = path.asByteStream()

        assertEquals(1024, stream.contentLength)
    }

    @Test
    fun testCanCreateByteStreamFromEmptyFileAndPathUsingTempFile() = runTest {
        val file = Files.createTempFile(null, null)
        val byteStream = file.asByteStream()
        assertEquals(0, byteStream.contentLength)

        val byteStreamFromPath = file.toAbsolutePath().asByteStream()
        assertEquals(0, byteStreamFromPath.contentLength)
    }

    @Test
    fun testCanCreateByteStreamFromEmptyFileAndPathUsingRandomFile() = runTest {
        val file = RandomTempFile(sizeInBytes = 0)
        val byteStream = file.asByteStream()
        assertEquals(0, byteStream.contentLength)

        val byteStreamFromPath = file.toPath().asByteStream()
        assertEquals(0, byteStreamFromPath.contentLength)
    }

    @Test
    fun testInputStreamAsByteStream() = runTest {
        binaryData.inputStream().use { inputStream ->
            val byteStream = inputStream.asByteStream()
            assertNull(byteStream.contentLength)
            assertTrue(byteStream.isOneShot)

            val output = byteStream.toByteArray()
            assertContentEquals(binaryData, output)
        }
    }

    @Test
    fun testInputStreamAsByteStreamWithLength() = runTest {
        binaryData.inputStream().use { inputStream ->
            val byteStream = inputStream.asByteStream(binaryData.size.toLong())
            assertEquals(binaryData.size.toLong(), byteStream.contentLength)
            assertTrue(byteStream.isOneShot)

            val output = byteStream.toByteArray()
            assertContentEquals(binaryData, output)
        }
    }

    @Test
    fun testByteStreamToOutputStream() = runTest {
        val byteStream = ByteStream.fromBytes(binaryData)
        ByteArrayOutputStream().use { outputStream ->
            byteStream.writeToOutputStream(outputStream)
            val output = outputStream.toByteArray()
            assertContentEquals(binaryData, output)
        }
    }

    @Test
    fun testWriteToByteStreamClosesOutput() = runTest {
        val byteStream = ByteStream.fromString("Hello")

        val sos = StatusTrackingOutputStream(ByteArrayOutputStream())

        assertFalse(sos.closed)
        byteStream.writeToOutputStream(sos)
        assertTrue(sos.closed)
    }

    @Test
    fun testAppendToByteStreamDoesNotCloseOutput() = runTest {
        val byteStream = ByteStream.fromString("Don't close me!")

        val sos = StatusTrackingOutputStream(ByteArrayOutputStream())

        assertFalse(sos.closed)
        byteStream.appendToOutputStream(sos)
        assertFalse(sos.closed)
    }

    private class StatusTrackingOutputStream(val os: OutputStream) : OutputStream() {
        var closed: Boolean = false

        override fun write(b: Int) {
            os.write(b)
        }

        override fun close() {
            closed = true
        }
    }
}
