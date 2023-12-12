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

class GzipSdkSourceTest {
    @Test
    fun testReadToByteArray() = runTest {
        val payload = "Hello World"
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipSdkSource = GzipSdkSource(bytes.source())

        val compressedBytes = gzipSdkSource.readToByteArray()
        gzipSdkSource.close()

        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testRead() = runTest {
        val payload = "Hello World"
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipSdkSource = GzipSdkSource(bytes.source())
        val tempBuffer = SdkBuffer()
        val limit = 1L

        while (gzipSdkSource.read(tempBuffer, limit) != -1L);
        gzipSdkSource.close()

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }

    @Test
    fun testReadLargeBody() = runTest {
        val payload = "Hello World".repeat(1600)

        println("\n\n\n${payload.length}\n\n\n")

        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipSdkSource = GzipSdkSource(bytes.source())
        val tempBuffer = SdkBuffer()
        val limit = 1L

        while (gzipSdkSource.read(tempBuffer, limit) != -1L);
        gzipSdkSource.close()

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

        val gzipSdkSource = GzipSdkSource(bytes.source())
        val tempBuffer = SdkBuffer()
        val limit = 1_000L

        while (gzipSdkSource.read(tempBuffer, limit) != -1L);
        gzipSdkSource.close()

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

        val gzipSdkSource = GzipSdkSource(bytes.source())
        val tempBuffer = SdkBuffer()
        val limit = 1_000L

        while (gzipSdkSource.read(tempBuffer, limit) != -1L);
        gzipSdkSource.close()

        val compressedBytes = tempBuffer.readByteArray()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }
}
