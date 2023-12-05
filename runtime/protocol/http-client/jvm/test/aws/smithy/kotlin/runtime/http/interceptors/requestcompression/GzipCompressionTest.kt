/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.hashing.crc32
import aws.smithy.kotlin.runtime.http.interceptors.decompressGzipBytes
import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GzipCompressionTest {
    @Test
    fun testBytesCompression() {
        runBytesCompressionTest("")
        runBytesCompressionTest("<Foo>bar</Foo>")
        runBytesCompressionTest("<Baz>foo</Baz>".repeat(100))
    }

    @Test
    fun testGzipSdkSourceCompression() = runTest {
        // "readFully" implementation doesn't allow for a read() on empty payloads and will fail test because of that so skipping it
        runGzipSdkSourceCompressionTest("", 1L, true)
        runGzipSdkSourceCompressionTest("", 1001L, true)

        runGzipSdkSourceCompressionTest("<Qux>baz</Qux>", 1L)
        runGzipSdkSourceCompressionTest("<Qux>baz</Qux>", 2L)
        runGzipSdkSourceCompressionTest("<Bar>foo</Bar>", 1002L)

        runGzipSdkSourceCompressionTest(
            "“Foo Bar” are very commonly seen as variables in samples and examples. Some sources will claim " +
                "that this is an interpretation of “F.U.B.A.R.”, a military term...",
            3L,
        )
        runGzipSdkSourceCompressionTest(
            "“Foo Bar” are very commonly seen as variables in samples and examples. Some sources will claim " +
                "that this is an interpretation of “F.U.B.A.R.”, a military term...",
            1003L,
        )

        runGzipSdkSourceCompressionTest(
            "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                " tantas postea doming.",
            1L,
        )
        runGzipSdkSourceCompressionTest(
            "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                " tantas postea doming.",
            1005L,
        )
    }

    @Test
    fun testGzipByteReadChannelCompression() = runTest {
        // "readFully" implementation doesn't allow for a read() on empty payloads and will fail test because of that so skipping it
        runGzipByteReadChannelCompressionTest("", 1L, true)
        runGzipByteReadChannelCompressionTest("", 1001L, true)

        runGzipByteReadChannelCompressionTest("<Qux>baz</Qux>", 1L)
        runGzipByteReadChannelCompressionTest("<Qux>baz</Qux>", 2L)
        runGzipByteReadChannelCompressionTest("<Bar>foo</Bar>", 1002L)

        runGzipByteReadChannelCompressionTest(
            "“Foo Bar” are very commonly seen as variables in samples and examples. Some sources will claim " +
                "that this is an interpretation of “F.U.B.A.R.”, a military term...",
            3L,
        )
        runGzipByteReadChannelCompressionTest(
            "“Foo Bar” are very commonly seen as variables in samples and examples. Some sources will claim " +
                "that this is an interpretation of “F.U.B.A.R.”, a military term...",
            1003L,
        )

        runGzipByteReadChannelCompressionTest(
            "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                " tantas postea doming.",
            1L,
        )
        runGzipByteReadChannelCompressionTest(
            "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                " tantas postea doming.",
            1005L,
        )
    }
}

private fun runBytesCompressionTest(payload: String) {
    val bytes = payload.encodeToByteArray()
    val bytesHash = bytes.crc32()

    val compressedBytes = compressBytes(bytes)
    val decompressedBytes = decompressGzipBytes(compressedBytes)

    assertContentEquals(bytes, decompressedBytes)
    assertEquals(bytesHash, decompressedBytes.crc32())
}

private fun runGzipSdkSourceCompressionTest(payload: String, limit: Long, skipReadFully: Boolean = false) {
    val readingMethods = listOf<(GzipSdkSource, SdkBuffer, Long, Long) -> ByteArray?> (
        { gzipSdkSource, _, _, _ ->
            runBlocking {
                gzipSdkSource.readToByteArray()
            }
        },

        { gzipSdkSource, tempBuffer, payloadSize, readLimit ->
            if (skipReadFully) {
                while (gzipSdkSource.read(tempBuffer, readLimit) != -1L);
            } else {
                gzipSdkSource.readFully(tempBuffer, payloadSize)
            }
            null
        },

        { gzipSdkSource, tempBuffer, _, readLimit ->
            while (gzipSdkSource.read(tempBuffer, readLimit) != -1L);
            null
        },
    )

    readingMethods.forEach { readSourceCompletely ->
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipSdkSource = GzipSdkSource(bytes.source(), bytes.size.toLong())
        val tempBuffer = SdkBuffer()

        val compressedByteArray = readSourceCompletely(gzipSdkSource, tempBuffer, bytes.size.toLong(), limit)
        gzipSdkSource.close()

        val compressedBytes = compressedByteArray ?: tempBuffer.readByteArray(); tempBuffer.close()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }
}

private suspend fun runGzipByteReadChannelCompressionTest(payload: String, limit: Long, skipReadFully: Boolean = false) {
    val readingMethods = listOf<(GzipByteReadChannel, SdkBuffer, Long, Long) -> Unit> (
        { gzipByteReadChannel, tempBuffer, _, _ ->
            runBlocking {
                gzipByteReadChannel.readAll(tempBuffer)
            }
        },

        { gzipByteReadChannel, tempBuffer, payloadSize, readLimit ->
            runBlocking {
                if (skipReadFully) {
                    do { gzipByteReadChannel.read(tempBuffer, readLimit) } while (!gzipByteReadChannel.isClosedForRead)
                } else {
                    gzipByteReadChannel.readFully(tempBuffer, payloadSize)
                }
            }
        },

        { gzipByteReadChannel, tempBuffer, _, readLimit ->
            runBlocking {
                do { gzipByteReadChannel.read(tempBuffer, readLimit) } while (!gzipByteReadChannel.isClosedForRead)
            }
        },
    )

    readingMethods.forEach { readChannelCompletely ->
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        val gzipByteReadChannel = GzipByteReadChannel(SdkByteReadChannel(bytes))
        val tempBuffer = SdkBuffer()

        readChannelCompletely(gzipByteReadChannel, tempBuffer, bytes.size.toLong(), limit)
        gzipByteReadChannel.cancel(null)

        val compressedBytes = tempBuffer.readByteArray(); tempBuffer.close()
        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }
}
