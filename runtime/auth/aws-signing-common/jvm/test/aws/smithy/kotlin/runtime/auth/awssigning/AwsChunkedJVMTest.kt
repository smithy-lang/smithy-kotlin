/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.*
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.nio.ByteBuffer
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AwsChunkedJVMTest {

    private val CHUNK_SIGNATURE_REGEX = Regex("chunk-signature=[a-zA-Z0-9]{64}") // alphanumeric, length of 64
    private val CHUNK_SIZE_REGEX = Regex("[0-9a-f]+;chunk-signature=") // hexadecimal, any length, immediately followed by the chunk signature

    private val CHUNK_SIZE_BYTES = AbstractAwsChunked.CHUNK_SIZE_BYTES

    private val testSigner = DefaultAwsSigner
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

    @Test
    fun testReadAvailableExactBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = dataLengthBytes + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4 +
            (1 + 1 + "chunk-signature=".length + 64 + 4)

        var sink = ByteArray(numBytesToRead)
        val BUFFER_SIZE = 1024
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        var bytesRead = 0
        while (bytesRead != numBytesToRead) {
            bytesRead += awsChunked.readAvailable(buffer)

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

        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])
        assertEquals(0, chunkSizes[1])

        assertTrue { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadAvailableExcessiveBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read excess of chunk data
        val numBytesToRead = dataLengthBytes * 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4 +
            (1 + 1 + "chunk-signature=".length + 64 + 4)

        var sink = ByteArray(numBytesToRead)
        val BUFFER_SIZE = 1024
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        while (awsChunked.readAvailable(buffer) != -1) {
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

        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])
        assertEquals(0, chunkSizes[1])

        assertTrue { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadAvailableFewerBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read excess of chunk data
        val numBytesToRead = dataLengthBytes / 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4 +
            (1 + 1 + "chunk-signature=".length + 64 + 4)

        var sink = byteArrayOf()
        val BUFFER_SIZE = 1024
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        var bytesRead = 0
        while (bytesRead <= numBytesToRead) {
            val currBytesRead = awsChunked.readAvailable(buffer)
            if (currBytesRead == -1) { break }
            bytesRead += currBytesRead

            while (buffer.remaining() > 0) {
                sink += buffer.get()
            }
            buffer.clear()
        }

        val sinkAsString = sink.decodeToString()
        println(sinkAsString)

        val chunkSignatures = getChunkSignatures(sinkAsString)
        assertEquals(1, chunkSignatures.size)
        val chunkSizes = getChunkSizes(sinkAsString)
        assertEquals(1, chunkSizes.size)

        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        assertFalse { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadAvailableWithTrailingHeaders() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val trailingHeaders = Headers {
            append("x-amz-checksum-crc32", "AAAAAA==")
            append("x-amz-arbitrary-header-with-value", "THIS IS A TEST!")
        }
        val trailingHeadersLength = getTrailingHeadersLength(trailingHeaders)

        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature, trailingHeaders)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = CHUNK_SIZE_BYTES + CHUNK_SIZE_BYTES.toString(16).length + 1 +
            ("chunk-signature=".length + 64 + 4) +
            (1 + 1 + "chunk-signature=".length + 64 + 2) + trailingHeadersLength + 2

        var sink = ByteArray(numBytesToRead)
        val BUFFER_SIZE = 1024
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        var bytesRead = 0
        while (bytesRead != numBytesToRead) {
            bytesRead += awsChunked.readAvailable(buffer)

            while (buffer.remaining() > 0) {
                sink += buffer.get()
            }

            buffer.clear()
        }

        assertEquals(numBytesToRead, bytesRead)

        val sinkAsString = sink.decodeToString()
        println(sinkAsString)

        val chunkSignatures = getChunkSignatures(sinkAsString)
        assertEquals(2, chunkSignatures.size)
        val chunkSizes = getChunkSizes(sinkAsString)
        assertEquals(2, chunkSizes.size)

        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])
        assertEquals(0, chunkSizes[1])

        val trailingHeaderBytes = sink.slice(sink.size - trailingHeadersLength - 2 until sink.size - "x-amz-trailer-signature:".length - 64 - 4).toByteArray()
        val expectedTrailerSignature = testSigner.signChunkTrailer(trailingHeaderBytes, expectedChunkSignature, testSigningConfig).signature
        val trailerSignature = getChunkTrailerSignature(sinkAsString)
        assertEquals(expectedTrailerSignature.decodeToString(), trailerSignature)

        assertTrue { awsChunked.isClosedForRead }
    }
}
