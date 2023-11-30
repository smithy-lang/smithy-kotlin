/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.hashing.crc32
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.test.runTest
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GzipJvmTest {
    private val payload = "<Foo>bar</Foo>"
    private val byteArray = payload.encodeToByteArray()
    private val byteArrayHash = byteArray.crc32()
    private val byteArraySource = byteArray.source()

    @Test
    fun testGzipSdkSource() = runTest {
        val gzipSdkSource = GzipSdkSource(byteArraySource)

        val tempBuffer = SdkBuffer()
        while (gzipSdkSource.read(tempBuffer, 1) != -1L);
        gzipSdkSource.close()

        val compressedByteArray = tempBuffer.readByteArray()
        tempBuffer.close()

        assertContentEquals(
            byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1, -77, 113, -53, -49, -73, 75, 74, 44, -78, -47, 7, 49, 0, 29, -105, -38, 89, 14, 0, 0, 0),
            compressedByteArray,
        )

        val decompressedPayload = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedPayload, payload)
        assertEquals(byteArrayHash, decompressedPayload.encodeToByteArray().crc32())
    }

    @Test
    fun testGzipByteReadChannel() = runTest {
        val gzipByteReadChannel = GzipByteReadChannel(byteArraySource.toSdkByteReadChannel())

        val answerBuffer = SdkBuffer()
        do {
            gzipByteReadChannel.read(answerBuffer, 1)
        }
        while (!gzipByteReadChannel.isClosedForRead)
        gzipByteReadChannel.cancel(null)

        val compressedByteArray = answerBuffer.readByteArray()
        answerBuffer.close()

        assertContentEquals(
            byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1, -77, 113, -53, -49, -73, 75, 74, 44, -78, -47, 7, 49, 0, 29, -105, -38, 89, 14, 0, 0, 0),
            compressedByteArray,
        )

        val decompressedPayload = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedPayload, payload)
        assertEquals(byteArrayHash, decompressedPayload.encodeToByteArray().crc32())
    }

    @Test
    fun testBytes() = runTest {
        val compressedByteArray = compressByteArray(byteArray).readAll()

        assertContentEquals(
            byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1, -77, 113, -53, -49, -73, 75, 74, 44, -78, -47, 7, 49, 0, 29, -105, -38, 89, 14, 0, 0, 0),
            compressedByteArray,
        )

        val decompressedPayload = GZIPInputStream(compressedByteArray?.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedPayload, payload)
        assertEquals(byteArrayHash, decompressedPayload.encodeToByteArray().crc32())
    }
}
