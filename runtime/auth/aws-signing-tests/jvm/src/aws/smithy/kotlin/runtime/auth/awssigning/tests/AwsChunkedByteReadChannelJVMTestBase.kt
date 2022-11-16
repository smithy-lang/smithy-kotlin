/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awssigning.AwsChunkedByteReadChannel
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.auth.awssigning.CHUNK_SIZE_BYTES
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import java.nio.ByteBuffer
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
public abstract class AwsChunkedByteReadChannelJVMTestBase: HasSigner {

    private val CHUNK_SIGNATURE_REGEX = Regex("chunk-signature=[a-zA-Z0-9]{64}") // alphanumeric, length of 64
    private val CHUNK_SIZE_REGEX = Regex("[0-9a-f]+;chunk-signature=") // hexadecimal, any length, immediately followed by the chunk signature

    private val testSigningConfig = AwsSigningConfig {
        region = "foo"
        service = "bar"
        signingDate = Instant.fromIso8601("20220427T012345Z")
        credentialsProvider = testCredentialsProvider
    }

    /**
     * Given a string representation of aws-chunked encoded bytes, return a list of the chunk signatures as strings.
     * Chunk signatures are defined by the following grammar:
     * chunk-signature=<64 alphanumeric characters>
     */
    private fun getChunkSignatures(bytes: String): List<String> = CHUNK_SIGNATURE_REGEX.findAll(bytes).map {
            result ->
        result.value.split("=")[1]
    }.toList()

    /**
     * Given a string representation of aws-chunked encoded bytes, returns a list of the chunk sizes as integers.
     * Chunk sizes are defined by the following grammar:
     * String(Hex(ChunkSize));chunk-signature=<chunk_signature>
     */
    private fun getChunkSizes(bytes: String): List<Int> = CHUNK_SIZE_REGEX.findAll(bytes).map {
            result ->
        result.value.split(";")[0].toInt(16)
    }.toList()

    /**
     * Given a string representation of aws-chunked encoded bytes, return the value of the x-amz-chunk-trailer trailing header.
     */
    private fun getChunkTrailerSignature(bytes: String): String? {
        val re = Regex("x-amz-trailer-signature:[a-zA-Z0-9]{64}")
        return re.findAll(bytes).map { result ->
            result.value.split(":")[1]
        }.toList().firstOrNull()
    }

    /**
     * Calculates the `aws-chunked` encoded trailing header length
     * Used to calculate how many bytes should be read for all the trailing headers to be consumed
     */
    private fun getTrailingHeadersLength(trailingHeaders: Headers) = trailingHeaders.entries().map {
            entry ->
        buildString {
            append(entry.key)
            append(":")
            append(entry.value.joinToString(","))
            append("\r\n")
        }.length
    }.reduce { acc, len -> acc + len } +
            "x-amz-trailer-signature:".length + 64 + "\r\n".length

    /**
     * Given the length of the chunk body, returns the length of the entire encoded chunk.
     * The chunk encoding structure is as follows:
     * String(Hex(CHUNK_SIZE));chunk-signature=<64 bytes>\r\n
     * <chunk payload>
     * \r\n
     *
     * @param chunkSize the size of the chunk
     * @return an integer representing the length of the encoded chunk data.
     * This is useful when calculating how many bytes to read in the test cases.
     */
    private fun encodedChunkLength(chunkSize: Int): Int {
        var length = chunkSize.toString(16).length +
                ";chunk-signature=".length +
                64 + // the chunk signature is always 64 bytes
                "\r\n".length

        if (chunkSize > 0) {
            length += chunkSize + "\r\n".length
        }

        return length
    }

    @Test
    public fun testReadAvailableExactBytes(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunkedByteReadChannel(chan, signer, testSigningConfig, previousSignature)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = encodedChunkLength(dataLengthBytes) + encodedChunkLength(0) + "\r\n".length

        var sink = ByteArray(numBytesToRead)
        val BUFFER_SIZE = 1024
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        var bytesRead = 0
        while (bytesRead != numBytesToRead) {
            bytesRead += awsChunked.readAvailable(buffer)

            buffer.flip()

            while (buffer.remaining() > 0) {
                sink += buffer.get()
            }

            buffer.clear()
        }

        assertEquals(numBytesToRead, bytesRead)

        val sinkAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(sinkAsString)
        assertEquals(2, chunkSignatures.size)
        val chunkSizes = getChunkSizes(sinkAsString)
        assertEquals(2, chunkSizes.size)

        var expectedChunkSignature = signer.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        expectedChunkSignature = signer.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])
        assertEquals(0, chunkSizes[1])

        assertTrue { awsChunked.isClosedForRead }
    }

    @Test
    public fun testReadAvailableExcessiveBytes(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunkedByteReadChannel(chan, signer, testSigningConfig, previousSignature)

        // read excess of chunk data
        val numBytesToRead = encodedChunkLength(dataLengthBytes * 2) + encodedChunkLength(0) + "\r\n".length

        var sink = ByteArray(numBytesToRead)
        val BUFFER_SIZE = 1024
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        while (awsChunked.readAvailable(buffer) != -1) {

            buffer.flip()

            while (buffer.remaining() > 0) {
                sink += buffer.get()
            }
            buffer.clear()
        }

        val sinkAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(sinkAsString)
        assertEquals(2, chunkSignatures.size)
        val chunkSizes = getChunkSizes(sinkAsString)
        assertEquals(2, chunkSizes.size)

        var expectedChunkSignature = signer.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        expectedChunkSignature = signer.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])
        assertEquals(0, chunkSizes[1])

        assertTrue { awsChunked.isClosedForRead }
    }

    @Test
    public fun testReadAvailableFewerBytes(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunkedByteReadChannel(chan, signer, testSigningConfig, previousSignature)

        // read excess of chunk data
        val numBytesToRead = encodedChunkLength(dataLengthBytes / 2) + encodedChunkLength(0) + "\r\n".length

        var sink = byteArrayOf()
        val BUFFER_SIZE = 1024
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        var bytesRead = 0
        while (bytesRead <= numBytesToRead) {
            val currBytesRead = awsChunked.readAvailable(buffer)
            if (currBytesRead == -1) { break }
            bytesRead += currBytesRead

            buffer.flip()

            while (buffer.remaining() > 0) {
                sink += buffer.get()
            }
            buffer.clear()
        }

        val sinkAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(sinkAsString)
        assertEquals(1, chunkSignatures.size)
        val chunkSizes = getChunkSizes(sinkAsString)
        assertEquals(1, chunkSizes.size)

        val expectedChunkSignature = signer.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        assertFalse { awsChunked.isClosedForRead }
    }

    @Test
    public fun testReadAvailableWithTrailingHeaders(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val trailingHeaders = Headers {
            append("x-amz-checksum-crc32", "AAAAAA==")
            append("x-amz-arbitrary-header-with-value", "THIS IS A TEST!")
        }
        val trailingHeadersLength = getTrailingHeadersLength(trailingHeaders)

        val awsChunked = AwsChunkedByteReadChannel(chan, signer, testSigningConfig, previousSignature, trailingHeaders)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = encodedChunkLength(CHUNK_SIZE_BYTES) + encodedChunkLength(0) + trailingHeadersLength + "\r\n".length

        var sink = ByteArray(numBytesToRead)
        val BUFFER_SIZE = 1024
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        var bytesRead = 0
        while (bytesRead != numBytesToRead) {
            bytesRead += awsChunked.readAvailable(buffer)

            buffer.flip()

            while (buffer.remaining() > 0) {
                sink += buffer.get()
            }

            buffer.clear()
        }

        assertEquals(numBytesToRead, bytesRead)

        val sinkAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(sinkAsString)
        assertEquals(2, chunkSignatures.size)
        val chunkSizes = getChunkSizes(sinkAsString)
        assertEquals(2, chunkSizes.size)

        var expectedChunkSignature = signer.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        expectedChunkSignature = signer.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])
        assertEquals(0, chunkSizes[1])

        val expectedTrailerSignature = signer.signChunkTrailer(trailingHeaders, expectedChunkSignature, testSigningConfig).signature
        val trailerSignature = getChunkTrailerSignature(sinkAsString)
        assertEquals(expectedTrailerSignature.decodeToString(), trailerSignature)

        assertTrue { awsChunked.isClosedForRead }
    }
}
