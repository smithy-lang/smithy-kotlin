/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.hashing.crc32
import aws.smithy.kotlin.runtime.http.interceptors.decompressGzipBytes
import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GzipCompressionTest {

    // TODO: Test different read methods other than just read.
    // TODO: Look at hashing source test for source of inspiration on this
    // TODO: Look at hashing byte read channel test for inspiration

    @Test
    fun testBytesCompressionSuite() {
        testBytesCompression("")
        testBytesCompression("<Foo>bar</Foo>")
        testBytesCompression("<Baz>foo</Baz>".repeat(100))
    }

    @Test
    fun testGzipSdkSourceCompressionSuite() = runTest {
        testGzipSdkSourceCompression("", 1L)
        testGzipSdkSourceCompression("", 1001L)

        testGzipSdkSourceCompression("<Qux>baz</Qux>", 1L)
        testGzipSdkSourceCompression("<Qux>baz</Qux>", 2L)
        testGzipSdkSourceCompression("<Bar>foo</Bar>", 1002L)

        testGzipSdkSourceCompression(
            "“Foo Bar” are very commonly seen as variables in samples and examples. Some sources will claim " +
                    "that this is an interpretation of “F.U.B.A.R.”, a military term",
            3L,
        )
        testGzipSdkSourceCompression(
                "“Foo Bar” are very commonly seen as variables in samples and examples. Some sources will claim " +
                        "that this is an interpretation of “F.U.B.A.R.”, a military term",
                1003L,
        )

        testGzipSdkSourceCompression(
                "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                        "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                        "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                        "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                        " tantas postea doming.",
                1004L,
        )
        testGzipSdkSourceCompression(
                "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                        "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                        "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                        "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                        " tantas postea doming.",
                1005L,
        )

//        // TODO: Read to byte array
//        val bytes = "<></>".encodeToByteArray()
//        val gzipSdkSource = GzipSdkSource(bytes.source())
//        val compressedBytes = gzipSdkSource.readToByteArray()
//        val decompressedBytes = decompressGzipBytes(compressedBytes)
//        assertContentEquals(bytes, decompressedBytes)

        // TODO: Read fully
        // FIXME
//        val bytes = "<></>".encodeToByteArray()
//        val gzipSdkSource = GzipSdkSource(bytes.source())
//        val tempBuffer = SdkBuffer()
//        gzipSdkSource.readFully(tempBuffer, bytes.size.toLong())
//        val compressedBytes = tempBuffer.readByteArray()
//        println(compressedBytes.toList()) // Only header is here
//        val decompressedBytes = decompressGzipBytes(compressedBytes)
//        assertContentEquals(bytes, decompressedBytes)
    }

    @Test
    fun testGzipByteReadChannelCompressionSuite() = runTest {
        testGzipByteReadChannelCompression("", 1L)
        testGzipByteReadChannelCompression("", 1001L)

        testGzipByteReadChannelCompression("<Qux>baz</Qux>", 1L)
        testGzipByteReadChannelCompression("<Qux>baz</Qux>", 2L)
        testGzipByteReadChannelCompression("<Bar>foo</Bar>", 1002L)

        testGzipByteReadChannelCompression(
                "“Foo Bar” are very commonly seen as variables in samples and examples. Some sources will claim " +
                        "that this is an interpretation of “F.U.B.A.R.”, a military term",
                3L,
        )
        testGzipByteReadChannelCompression(
                "“Foo Bar” are very commonly seen as variables in samples and examples. Some sources will claim " +
                        "that this is an interpretation of “F.U.B.A.R.”, a military term",
                1003L,
        )

        testGzipByteReadChannelCompression(
                "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                        "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                        "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                        "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                        " tantas postea doming.",
                1004L,
        )
        testGzipByteReadChannelCompression(
                "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                        "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                        "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                        "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                        " tantas postea doming.",
                1005L,
        )

        // TODO: Read fully
//        val bytes = "<></>".encodeToByteArray()
//        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
//        val tempBuffer = SdkBuffer()
//        gzipByteReadChannel.readFully(tempBuffer, bytes.size.toLong()) // TODO: Take into account bytes.size == 0
//        val decompressedBytes = decompressGzipBytes(tempBuffer.readByteArray())
//        assertContentEquals(bytes, decompressedBytes)

        // TODO: Read all
        // FIXME
//        val bytes = "<></>".encodeToByteArray()
//        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
//        val tempBuffer = SdkBuffer()
//        gzipByteReadChannel.readAll(tempBuffer)
//        val compressedBytes = tempBuffer.readByteArray()
//        println(compressedBytes)
//        val decompressedBytes = decompressGzipBytes(compressedBytes)
//        assertContentEquals(bytes, decompressedBytes)
    }
}

private fun testBytesCompression(payload: String) {
    val bytes = payload.encodeToByteArray()
    val bytesHash = bytes.crc32()

    val compressedBytes = compressBytes(bytes)
    val decompressedBytes = decompressGzipBytes(compressedBytes)

    assertContentEquals(decompressedBytes, bytes)
    assertEquals(bytesHash, decompressedBytes.crc32())
}

fun testGzipSdkSourceCompression(payload: String, limit: Long) {
    val readingMethods = listOf<(GzipSdkSource, SdkBuffer, Long, Long) -> Unit>(
//        {gzipSdkSource, _, _ -> runBlocking { gzipSdkSource.readToByteArray(); gzipSdkSource.close() } },
//        {gzipSdkSource, tempBuffer, readLimit, _ -> while (gzipSdkSource.read(tempBuffer, readLimit) != -1L); gzipSdkSource.close() },
//        {gzipSdkSource, tempBuffer, _, bytesInSource -> gzipSdkSource.readFully(tempBuffer, bytesInSource); gzipSdkSource.close() },
    )

    readingMethods.forEach { readSourceCompletely ->
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipSdkSource = GzipSdkSource(bytes.source())
        val tempBuffer = SdkBuffer()

//        readSourceCompletely(gzipSdkSource, tempBuffer, limit, bytes.size.toLong())

        while (gzipSdkSource.read(tempBuffer, limit) != -1L); gzipSdkSource.close()

//        val bytesToRead = if (bytes.isEmpty()) 0 else bytes.size - 1L
//        gzipSdkSource.readFully(tempBuffer, bytesToRead)

        val compressedBytes = tempBuffer.readByteArray(); tempBuffer.close()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(decompressedBytes, bytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }
}

private suspend fun testGzipByteReadChannelCompression(payload: String, limit: Long) {
    val bytes = payload.encodeToByteArray()
    val bytesHash = bytes.crc32()

    val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
    val tempBuffer = SdkBuffer()

    // TODO: Replace with all methods available and loop
    do { gzipByteReadChannel.read(tempBuffer, limit) } while (!gzipByteReadChannel.isClosedForRead)
    gzipByteReadChannel.cancel(null)

    val compressedBytes = tempBuffer.readByteArray(); tempBuffer.close()
    val decompressedBytes = decompressGzipBytes(compressedBytes)

    assertContentEquals(decompressedBytes, bytes)
    assertEquals(bytesHash, decompressedBytes.crc32())
}
