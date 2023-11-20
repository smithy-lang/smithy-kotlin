/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.http.toByteStream
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.io.toSdkByteReadChannel
import aws.smithy.kotlin.runtime.io.use
import kotlinx.coroutines.test.runTest
import java.util.zip.GZIPInputStream
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class GzipJvmTest {
    @Ignore
    @Test
    fun testGzipSdkSource() = runTest {
        // Creat gzip source
        val byteArray = "<Foo>bar</Foo>".encodeToByteArray()
        val byteArraySource = byteArray.source()
        val gzipSdkSource = GzipSdkSource(byteArraySource)

        // Read all gzip source to answer buffer
        val answerBuffer = SdkBuffer()
        while (gzipSdkSource.read(answerBuffer, 1) != -1L);
        gzipSdkSource.close()
        byteArraySource.close()

        // Extract byte array from buffer
        val compressedByteArray = answerBuffer.readByteArray()
        answerBuffer.close()

        // Decompress byte array and check it's good
        val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedByteArray, "<Foo>bar</Foo>")
    }

    @Ignore
    @Test
    fun testGzipByteReadChannel() = runTest {
        // Creat gzip source
        val byteArray = "<Foo>bar</Foo>".encodeToByteArray()
        val byteArraySource = byteArray.source()
        val gzipSdkSource = GzipByteReadChannel(byteArraySource.toSdkByteReadChannel())

        // Read all gzip source to answer buffer
        val answerBuffer = SdkBuffer()
        while (gzipSdkSource.read(answerBuffer, 1) != -1L);
        byteArraySource.close()

        // Extract byte array from buffer
        val compressedByteArray = answerBuffer.readByteArray()
        answerBuffer.close()

        // Decompress byte array and check it's good
        val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedByteArray, "<Foo>bar</Foo>")
    }

    @Test
    fun testBytes() = runTest {
        val byteArray = "<Foo>bar</Foo>".encodeToByteArray()
        val compressedByteArray = compressByteArray(byteArray).toByteStream()!!.toByteArray()

        val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedByteArray, "<Foo>bar</Foo>")
    }
}
