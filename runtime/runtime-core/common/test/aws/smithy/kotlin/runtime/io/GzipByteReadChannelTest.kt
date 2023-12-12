/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.crc32
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

expect fun decompressGzipBytes(bytes: ByteArray): ByteArray

class GzipByteReadChannelTest {
    @Test
    fun testReadAll() = runTest {
        val payload = "Hello World"
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()

        gzipByteReadChannel.readAll(tempBuffer)
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testReadToBuffer() = runTest {
        val payload = "Hello World".repeat(1600)
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))

        val tempBuffer = gzipByteReadChannel.readToBuffer()
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testReadRemaining() = runTest {
        val payload = "Hello World".repeat(1600)
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()

        gzipByteReadChannel.readRemaining(tempBuffer)
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testRead() = runTest {
        val payload = "Hello World"
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()
        val limit = 1L

        while (gzipByteReadChannel.read(tempBuffer, limit) != -1L);
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testReadLargeBody() = runTest {
        val payload = "Hello World".repeat(1600)
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()
        val limit = 1L

        while (gzipByteReadChannel.read(tempBuffer, limit) != -1L);
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testReadLargeLimit() = runTest {
        val payload = "Hello World"
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()
        val limit = 1_000L

        while (gzipByteReadChannel.read(tempBuffer, limit) != -1L);
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testReadLargeBodyLargeLimit() = runTest {
        val payload = "Hello World".repeat(1600)
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()
        val limit = 1_000L

        while (gzipByteReadChannel.read(tempBuffer, limit) != -1L);
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testIsClosedForRead() = runTest {
        val payload = "Hello World"
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()
        val limit = 1L

        do { gzipByteReadChannel.read(tempBuffer, limit) } while (!gzipByteReadChannel.isClosedForRead)
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testIsClosedForReadLargeBody() = runTest {
        val payload = "Hello World".repeat(1600)
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()
        val limit = 1L

        do { gzipByteReadChannel.read(tempBuffer, limit) } while (!gzipByteReadChannel.isClosedForRead)
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testIsClosedForReadLargeLimit() = runTest {
        val payload = "Hello World"
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()
        val limit = 1_000L

        do { gzipByteReadChannel.read(tempBuffer, limit) } while (!gzipByteReadChannel.isClosedForRead)
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testIsClosedForReadLargeBodyLargeLimit() = runTest {
        val payload = "Hello World".repeat(1600)
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()
        val limit = 1_000L

        do { gzipByteReadChannel.read(tempBuffer, limit) } while (!gzipByteReadChannel.isClosedForRead)
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }
}
