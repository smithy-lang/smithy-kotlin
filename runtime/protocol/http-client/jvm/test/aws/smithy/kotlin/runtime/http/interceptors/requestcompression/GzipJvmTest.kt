/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.http.toByteStream
import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.test.runTest
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class GzipJvmTest {
    @Test
    fun testGzipSdkSource() = runTest {
        val byteArray = "<Foo>bar</Foo>".encodeToByteArray()
        val byteArraySource = byteArray.source()
        val gzipSdkSource = GzipSdkSource(byteArraySource)

        val answerBuffer = SdkBuffer()
        while (gzipSdkSource.read(answerBuffer, 1) != -1L);
        gzipSdkSource.close()

        val compressedByteArray = answerBuffer.readByteArray()
        answerBuffer.close()

        val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedByteArray, "<Foo>bar</Foo>")
    }

    @Test
    fun testGzipByteReadChannel() = runTest {
        val byteArray = "<Foo>bar</Foo>".encodeToByteArray()
        val byteArraySource = byteArray.source()
        val gzipByteReadChannel = GzipByteReadChannel(byteArraySource.toSdkByteReadChannel())

        val answerBuffer = SdkBuffer()
        while (gzipByteReadChannel.read(answerBuffer, 1) != -1L);
        gzipByteReadChannel.cancel(null)

        val compressedByteArray = answerBuffer.readByteArray()
        answerBuffer.close()

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
