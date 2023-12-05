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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GzipCompressionTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "<Foo>bar</Foo>",
        ],
    )
    fun testBytesCompression(payload: String) {
        runBytesCompressionTest(payload)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                "tantas postea doming.",
        ],
    )
    fun testGzipSdkSourceCompression(payload: String) = runTest {
        runGzipSdkSourceCompressionTest(payload, 1L)
        runGzipSdkSourceCompressionTest(payload, 1000L)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "Lorem ipsum dolor sit amet, dolorem corpora iracundia has ea, duo cu stet alterum scriptorem, " +
                "et qui putent tractatos. Ne epicurei gloriatur pro, et ornatus consulatu necessitatibus qui. " +
                "Veri eripuit feugiat sed no, dicat ridens id quo. Mei ne putent impedit antiopam. Ad libris " +
                "assueverit his. Quo te brute vitae iuvaret, ut nibh bonorum sea. Mel altera vocibus ei, no vel" +
                "tantas postea doming.",
        ],
    )
    fun testGzipByteReadChannelCompression(payload: String) = runTest {
        runGzipByteReadChannelCompressionTest(payload, 1L)
        runGzipByteReadChannelCompressionTest(payload, 1000L)
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

private fun runGzipSdkSourceCompressionTest(payload: String, limit: Long) {
    val readingMethods = listOf<(GzipSdkSource, SdkBuffer, Long, Long, Boolean) -> ByteArray?> (
        { gzipSdkSource, _, _, _, _ ->
            runBlocking {
                gzipSdkSource.readToByteArray()
            }
        },

        { gzipSdkSource, tempBuffer, payloadSize, readLimit, skipReadFully ->
            // "readFully" implementation doesn't allow for a read() on empty payloads and will fail test because of that so using while loop instead
            if (skipReadFully || payloadSize == 0L) {
                while (gzipSdkSource.read(tempBuffer, readLimit) != -1L);
            } else {
                gzipSdkSource.readFully(tempBuffer, payloadSize)
            }
            null
        },

        { gzipSdkSource, tempBuffer, _, readLimit, _ ->
            while (gzipSdkSource.read(tempBuffer, readLimit) != -1L);
            null
        },
    )

    readingMethods.forEach { readSourceCompletely ->
        val bytes = payload.encodeToByteArray()
        val bytesHash = bytes.crc32()

        listOf(
            GzipSdkSourceWrapper(
                GzipSdkSource(bytes.source(), bytes.size.toLong()),
                false,
            ),
            GzipSdkSourceWrapper(
                GzipSdkSource(bytes.source()),
                true,
            ),
        ).forEach { gzip ->
            val tempBuffer = SdkBuffer()

            val compressedByteArray = readSourceCompletely(
                gzip.gzipSdkSource,
                tempBuffer,
                bytes.size.toLong(),
                limit,
                gzip.shouldSkipReadFully,
            )
            gzip.gzipSdkSource.close()

            val compressedBytes = compressedByteArray ?: tempBuffer.readByteArray()
            tempBuffer.close()

            val decompressedBytes = decompressGzipBytes(compressedBytes)

            assertContentEquals(bytes, decompressedBytes)
            assertEquals(bytesHash, decompressedBytes.crc32())
        }
    }
}

private data class GzipSdkSourceWrapper(val gzipSdkSource: GzipSdkSource, val shouldSkipReadFully: Boolean)

private suspend fun runGzipByteReadChannelCompressionTest(payload: String, limit: Long) {
    val readingMethods = listOf<(GzipByteReadChannel, SdkBuffer, Long, Long) -> Unit> (
        { gzipByteReadChannel, tempBuffer, _, _ ->
            runBlocking {
                gzipByteReadChannel.readAll(tempBuffer)
            }
        },

        { gzipByteReadChannel, tempBuffer, payloadSize, readLimit ->
            runBlocking {
                // "readFully" implementation doesn't allow for a read() on empty payloads and will fail test because of that so using while loop instead
                if (payloadSize == 0L) {
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

        val compressedBytes = tempBuffer.readByteArray()
        tempBuffer.close()

        val decompressedBytes = decompressGzipBytes(compressedBytes)

        assertContentEquals(bytes, decompressedBytes)
        assertEquals(bytesHash, decompressedBytes.crc32())
    }
}
