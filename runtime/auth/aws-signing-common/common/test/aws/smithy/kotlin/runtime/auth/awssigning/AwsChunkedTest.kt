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
import kotlin.random.Random
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AwsChunkedTest {

    private val CHUNK_SIGNATURE_REGEX = Regex("chunk-signature=[a-zA-Z0-9]{64}") // alphanumeric, length of 64
    private val CHUNK_SIZE_REGEX = Regex("[0-9a-f]+;chunk-signature=") // hexadecimal, any length, immediately followed by the chunk signature

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
    fun testReadRemainingExactBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read the exact chunk length
        val bytes = awsChunked.readRemaining(
            CHUNK_SIZE_BYTES + CHUNK_SIZE_BYTES.toString(16).length + 1 +
                "chunk-signature=".length + 64 + 4 + 1 + 1 + "chunk-signature=".length + 64 + 4 + 2,
        )
        val bytesAsString = bytes.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 2) // chunk of data plus an empty chunk
        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        // the second chunk signature should come from the chunk of zero length
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)

        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadRemainingFewerBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = CHUNK_SIZE_BYTES / 2 // read ~half of the chunk data
        val bytes = awsChunked.readRemaining(numBytesToRead)
        val bytesAsString = bytes.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 1) // we should only have the signature of the first chunk
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 1)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)

        assertFalse { awsChunked.isClosedForRead }
        assertEquals(bytes.size, numBytesToRead)
    }

    @Test
    fun testReadRemainingExcessiveBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = 131072 // read double of the available chunk data
        val bytes = awsChunked.readRemaining(numBytesToRead)
        val bytesAsString = bytes.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 2)
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)

        // the SdkByteReadChannel should be fully exhausted
        assertTrue { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadRemainingMultipleFullChunks() = runTest {
        val numChunks = 5
        val dataLengthBytes = CHUNK_SIZE_BYTES * numChunks
        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * numChunks + 1 + 1 + "chunk-signature=".length + 64 + 4

        val bytes = awsChunked.readRemaining(numBytesToRead) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = bytes.decodeToString()

        assertEquals(bytes.size, numBytesToRead)
        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = getChunkSignatures(bytesAsString)
        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSignatures.size, numChunks + 1)
        assertEquals(chunkSizes.size, numChunks + 1)

        // validate each chunk signature
        for (chunk in 0 until numChunks) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
            assertEquals(CHUNK_SIZE_BYTES, chunkSizes[chunk])
        }
    }

    @Test
    fun testReadRemainingMultipleChunksLastChunkNotFull() = runTest {
        val numChunks = 6
        val dataLengthBytes = CHUNK_SIZE_BYTES * (numChunks - 1) + CHUNK_SIZE_BYTES / 2 // 5 full chunks, 1 half-full chunk

        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes +
            (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * (numChunks - 1) +
            (4 + 1 + "chunk-signature=".length + 64 + 4) + // last chunk
            (1 + 1 + "chunk-signature=".length + 64 + 4) // terminating chunk (zero length)

        val bytes = awsChunked.readRemaining(numBytesToRead) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = bytes.decodeToString()

        assertEquals(bytes.size, numBytesToRead)
        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = getChunkSignatures(bytesAsString)
        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSignatures.size, numChunks + 1)
        assertEquals(chunkSizes.size, numChunks + 1)

        // validate each chunk signature
        for (chunk in 0 until numChunks - 1) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
            assertEquals(CHUNK_SIZE_BYTES, chunkSizes[chunk])
        }

        // validate the last chunk
        var expectedChunkSignature = testSigner.signChunk(
            data.slice(CHUNK_SIZE_BYTES * (numChunks - 1) until data.size).toByteArray(),
            previousSignature,
            testSigningConfig,
        ).signature
        previousSignature = expectedChunkSignature

        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunkSignatures.size - 2])
        assertEquals(CHUNK_SIZE_BYTES / 2, chunkSizes[chunkSizes.size - 2])

        // validate terminal chunk
        expectedChunkSignature = testSigner.signChunk(
            byteArrayOf(),
            previousSignature,
            testSigningConfig,
        ).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
        assertEquals(0, chunkSizes.last())
    }

    @Test
    fun testReadRemainingWithTrailingHeaders() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()

        val trailingHeaders = Headers {
            append("x-amz-checksum-crc32", "AAAAAA==")
            append("x-amz-arbitrary-header-with-value", "BOOYAH")
        }

        val trailingHeadersLength = getTrailingHeadersLength(trailingHeaders)

        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature, trailingHeaders)

        val numBytesToRead = CHUNK_SIZE_BYTES + CHUNK_SIZE_BYTES.toString(16).length + 1 +
            ("chunk-signature=".length + 64 + 4) +
            (1 + 1 + "chunk-signature=".length + 64 + 2) + trailingHeadersLength + 2
        val bytes = awsChunked.readRemaining(numBytesToRead)
        val bytesAsString = bytes.decodeToString()

        assertEquals(numBytesToRead, bytes.size)

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 2) // chunk of data plus an empty chunk
        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        // the second chunk signature should come from the chunk of zero length
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), previousSignature, testSigningConfig).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)

        val trailingHeaderBytes = bytes.slice(bytes.size - trailingHeadersLength - 2 until bytes.size - "x-amz-trailer-signature:".length - 64 - 4).toByteArray()
        val expectedTrailerSignature = testSigner.signChunkTrailer(trailingHeaderBytes, previousSignature, testSigningConfig).signature
        val trailerSignature = getChunkTrailerSignature(bytesAsString)
        assertEquals(expectedTrailerSignature.decodeToString(), trailerSignature)

        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadFullyNegativeOffset() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val sink = ByteArray(dataLengthBytes)

        assertFailsWith<IllegalArgumentException> {
            awsChunked.readFully(sink, -500, dataLengthBytes)
        }
    }

    @Test
    fun testReadFullyOffsetTooLarge() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)
        val sink = ByteArray(dataLengthBytes)

        assertFailsWith<IllegalArgumentException> {
            awsChunked.readFully(sink, 0, sink.size * 2) // try to read double the size available in the sink
        }
    }

    @Test
    fun testReadFullyExactBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = dataLengthBytes + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4 + 1 + 1 + "chunk-signature=".length + 64 + 4

        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead)
        val bytesAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 2) // chunk of data plus an empty chunk
        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        // the second chunk signature should come from the chunk of zero length
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)

        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadFullyFewerBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes / 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4

        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead)
        val bytesAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 1) // chunk of data plus an empty chunk
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 1)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)

        assertFalse { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadFullyExcessiveBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)
        val numBytesToRead = dataLengthBytes * 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4
        val sink = ByteArray(numBytesToRead)

        assertFailsWith<RuntimeException> {
            awsChunked.readFully(sink, 0, numBytesToRead)
        }
    }

    @Test
    fun testReadFullyWithNonZeroOffset() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = dataLengthBytes + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4 + 1 + 1 + "chunk-signature=".length + 64 + 4

        val offset = 128
        val sink = ByteArray(numBytesToRead + offset) { 0 }
        awsChunked.readFully(sink, offset, numBytesToRead)

        for (index in 0..127) { // make sure the default value has not been overridden for the first `offset` bytes
            assertEquals(0, sink.get(index))
        }
        val bytesAsString = sink.slice(128 until sink.size).toByteArray().decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(2, chunkSignatures.size) // chunk of data plus an empty chunk
        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(2, chunkSizes.size)
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])
        assertEquals(0, chunkSizes[1])

        assertTrue { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadFullyOnAlreadyClosedChannel() = runTest {
        val dataLengthBytes = 0
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        assertTrue(chan.isClosedForRead) // no chunk data available

        // read the chunk of zero length
        val numBytesToRead = 1 + 1 + "chunk-signature=".length + 64 + 4
        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead)

        val chunkSignatures = getChunkSignatures(sink.decodeToString())
        val chunkSizes = getChunkSizes(sink.decodeToString())
        assertEquals(1, chunkSignatures.size)
        assertEquals(testSigner.signChunk(byteArrayOf(), previousSignature, testSigningConfig).signature.decodeToString(), chunkSignatures[0])
        assertEquals(1, chunkSizes.size)
        assertEquals(0, chunkSizes[0])

        // now the chunks are all exhausted. try to read some data again, expecting failure.
        assertFailsWith<RuntimeException> {
            awsChunked.readFully(sink, 0, numBytesToRead)
        }
    }

    @Test
    fun testReadFullyZeroBytesOnAlreadyClosedChannel() = runTest {
        val dataLengthBytes = 0
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)
        val sink = ByteArray(0)

        assertTrue(chan.isClosedForRead)
        awsChunked.readFully(sink, 0, 0)
    }

    @Test
    fun testReadFullyMultipleFullChunks() = runTest {
        val numChunks = 5
        val dataLengthBytes = CHUNK_SIZE_BYTES * numChunks
        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * numChunks + 1 + 1 + "chunk-signature=".length + 64 + 4
        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = sink.decodeToString()

        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(numChunks + 1, chunkSignatures.size) // chunks of data plus an empty chunk
        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(numChunks + 1, chunkSizes.size)

        // validate each chunk signature and size
        for (chunk in 0 until numChunks) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
            assertEquals(CHUNK_SIZE_BYTES, chunkSizes[chunk])
        }
        val expectedChunkSignature = testSigner.signChunk(byteArrayOf(), previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
        assertEquals(0, chunkSizes.last())
    }

    @Test
    fun testReadFullyMultipleChunksLastChunkNotFull() = runTest {
        val numChunks = 6
        val dataLengthBytes = CHUNK_SIZE_BYTES * (numChunks - 1) + CHUNK_SIZE_BYTES / 2 // 5 full chunks, 1 half-full chunk

        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes +
            (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * (numChunks - 1) +
            ((CHUNK_SIZE_BYTES / 2).toString(16).length + 1 + "chunk-signature=".length + 64 + 4) + // last chunk
            (1 + 1 + "chunk-signature=".length + 64 + 4) // terminating chunk

        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = sink.decodeToString()

        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(numChunks + 1, chunkSignatures.size) // chunks of data plus an empty chunk
        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(numChunks + 1, chunkSizes.size)

        // validate each chunk signature
        for (chunk in 0 until numChunks - 1) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
            assertEquals(CHUNK_SIZE_BYTES, chunkSizes[chunk])
        }

        // validate the last chunk
        var expectedChunkSignature = testSigner.signChunk(
            data.slice(CHUNK_SIZE_BYTES * (numChunks - 1) until data.size).toByteArray(),
            previousSignature,
            testSigningConfig,
        ).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunkSignatures.size - 2])
        assertEquals(CHUNK_SIZE_BYTES / 2, chunkSizes[chunkSizes.size - 2])

        // validate terminal chunk
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
        assertEquals(0, chunkSizes.last())
    }

    @Test
    fun testReadFullyWithTrailingHeaders() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()

        val trailingHeaders = Headers {
            append("x-amz-checksum-crc32", "AAAAAA==")
            append("x-amz-arbitrary-header-with-value", "UMM")
        }

        val trailingHeadersLength = getTrailingHeadersLength(trailingHeaders)

        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature, trailingHeaders)

        val numBytesToRead = CHUNK_SIZE_BYTES + CHUNK_SIZE_BYTES.toString(16).length + 1 +
            ("chunk-signature=".length + 64 + 4) +
            (1 + 1 + "chunk-signature=".length + 64 + 2) + trailingHeadersLength + 2

        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead)
        val bytesAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 2) // chunk of data plus an empty chunk
        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        // the second chunk signature should come from the chunk of zero length
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), previousSignature, testSigningConfig).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)

        val trailingHeaderBytes = sink.slice(sink.size - trailingHeadersLength - 2 until sink.size - "x-amz-trailer-signature:".length - 64 - 4).toByteArray()
        val expectedTrailerSignature = testSigner.signChunkTrailer(trailingHeaderBytes, previousSignature, testSigningConfig).signature
        val trailerSignature = getChunkTrailerSignature(bytesAsString)
        assertEquals(expectedTrailerSignature.decodeToString(), trailerSignature)

        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadAvailableNegativeOffset() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val sink = ByteArray(dataLengthBytes)

        assertFailsWith<IllegalArgumentException> {
            awsChunked.readAvailable(sink, -500, dataLengthBytes)
        }
    }

    @Test
    fun testReadAvailableOffsetTooLarge() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)
        val sink = ByteArray(dataLengthBytes)

        assertFailsWith<IllegalArgumentException> {
            awsChunked.readAvailable(sink, 0, sink.size * 2) // try to read double the size available in the sink
        }
    }

    @Test
    fun testReadAvailableExactBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = dataLengthBytes + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4 + (1 + 1 + "chunk-signature=".length + 64 + 4)

        val sink = ByteArray(numBytesToRead)
        // need to make 2 successive calls because there are two chunks -- readAvailable will only fetch the first one to avoid potential suspensions
        var bytesRead = awsChunked.readAvailable(sink, 0, numBytesToRead)
        bytesRead += awsChunked.readAvailable(sink, bytesRead, numBytesToRead - bytesRead)

        val bytesAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(2, chunkSignatures.size) // chunk of data plus an empty chunk
        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(2, chunkSizes.size)
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])
        assertEquals(0, chunkSizes[1])

        assertEquals(numBytesToRead, bytesRead)
        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadAvailableExcessiveBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes * 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4
        val sink = ByteArray(numBytesToRead * 2)

        var bytesRead = awsChunked.readAvailable(sink, 0, numBytesToRead)
        bytesRead += awsChunked.readAvailable(sink, bytesRead, numBytesToRead)

        val bytesAsString = sink.decodeToString()
        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(2, chunkSignatures.size) // chunk of data plus an empty chunk
        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), expectedChunkSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(2, chunkSizes.size)
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])
        assertEquals(0, chunkSizes[1])

        assertNotEquals(bytesRead, numBytesToRead) // because we requested more bytes than were available
        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadAvailableFewerBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes / 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4

        val sink = ByteArray(numBytesToRead)
        val bytesRead = awsChunked.readAvailable(sink, 0, numBytesToRead)
        assertEquals(numBytesToRead, bytesRead)

        val bytesAsString = sink.decodeToString()
        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(1, chunkSignatures.size)
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(1, chunkSizes.size)
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        assertFalse { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadAvailableMultipleFullChunks() = runTest {
        val numChunks = 5
        val dataLengthBytes = CHUNK_SIZE_BYTES * numChunks
        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * numChunks + (1 + 1 + "chunk-signature=".length + 64 + 4)
        val sink = ByteArray(numBytesToRead)

        var bytesRead = 0
        for (chunk in 0 until numChunks) { // read the chunks in a loop
            bytesRead += awsChunked.readAvailable(sink, bytesRead, CHUNK_SIZE_BYTES + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4))
        }
        bytesRead += awsChunked.readAvailable(sink, bytesRead, (1 + 1 + "chunk-signature=".length + 64 + 4))
        assertEquals(numBytesToRead, bytesRead)

        val bytesAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(numChunks + 1, chunkSignatures.size)
        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(numChunks + 1, chunkSizes.size)

        // validate each chunk signature
        for (chunk in 0 until numChunks) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
            assertEquals(CHUNK_SIZE_BYTES, chunkSizes[chunk])
        }

        // validate the terminal chunk
        val expectedChunkSignature = testSigner.signChunk(byteArrayOf(), previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
        assertEquals(0, chunkSizes.last())

        assertTrue { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadAvailableMultipleChunksLastChunkNotFull() = runTest {
        val numChunks = 6
        val dataLengthBytes = CHUNK_SIZE_BYTES * (numChunks - 1) + CHUNK_SIZE_BYTES / 2 // 5 full chunks, 1 half-full chunk

        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes +
            (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * (numChunks - 1) +
            ((CHUNK_SIZE_BYTES / 2).toString(16).length + 1 + "chunk-signature=".length + 64 + 4) + // last chunk
            (1 + 1 + "chunk-signature=".length + 64 + 4) // terminal chunk

        val sink = ByteArray(numBytesToRead)

        var bytesRead = 0
        for (chunk in 0 until numChunks - 1) {
            bytesRead += awsChunked.readAvailable(sink, bytesRead, CHUNK_SIZE_BYTES + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4)) // read all the underlying data plus any chunk signature overhead
        }
        // read the last chunk, which is smaller in size
        bytesRead += awsChunked.readAvailable(sink, bytesRead, CHUNK_SIZE_BYTES / 2 + (CHUNK_SIZE_BYTES / 2).toString(16).length + 1 + "chunk-signature=".length + 64 + 4)

        // read the terminal chunk
        bytesRead += awsChunked.readAvailable(sink, bytesRead, 1 + 1 + "chunk-signature=".length + 64 + 4)

        val bytesAsString = sink.decodeToString()

        assertEquals(numBytesToRead, bytesRead)
        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(numChunks + 1, chunkSignatures.size)
        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(numChunks + 1, chunkSizes.size)

        // validate each chunk signature
        for (chunk in 0 until numChunks - 1) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
            assertEquals(CHUNK_SIZE_BYTES, chunkSizes[chunk])
        }

        // validate the last chunk
        var expectedChunkSignature = testSigner.signChunk(
            data.slice(CHUNK_SIZE_BYTES * (numChunks - 1) until data.size).toByteArray(),
            previousSignature,
            testSigningConfig,
        ).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunkSignatures.size - 2])
        assertEquals(CHUNK_SIZE_BYTES / 2, chunkSizes[chunkSizes.size - 2])

        // validate terminal chunk
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), previousSignature, testSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
        assertEquals(0, chunkSizes.last())
    }

    @Test
    fun testReadAvailableWithTrailingHeaders() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        var previousSignature: ByteArray = byteArrayOf()

        val trailingHeaders = Headers {
            append("x-amz-checksum-crc32", "AAAAAA==")
            append("x-amz-arbitrary-header-with-value", "UMM")
        }

        val trailingHeadersLength = getTrailingHeadersLength(trailingHeaders)

        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature, trailingHeaders)

        val numBytesToRead = CHUNK_SIZE_BYTES + CHUNK_SIZE_BYTES.toString(16).length + 1 +
            ("chunk-signature=".length + 64 + 4) +
            (1 + 1 + "chunk-signature=".length + 64 + 2) + trailingHeadersLength + 2

        val sink = ByteArray(numBytesToRead)

        var bytesRead = 0

        while (bytesRead != numBytesToRead) {
            bytesRead += awsChunked.readAvailable(sink, bytesRead, numBytesToRead - bytesRead)
        }
        val bytesAsString = sink.decodeToString()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 2) // chunk of data plus an empty chunk
        var expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        // the second chunk signature should come from the chunk of zero length
        expectedChunkSignature = testSigner.signChunk(byteArrayOf(), previousSignature, testSigningConfig).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)

        val trailingHeaderBytes = sink.slice(sink.size - trailingHeadersLength - 2 until sink.size - "x-amz-trailer-signature:".length - 64 - 4).toByteArray()
        val expectedTrailerSignature = testSigner.signChunkTrailer(trailingHeaderBytes, previousSignature, testSigningConfig).signature
        val trailerSignature = getChunkTrailerSignature(bytesAsString)
        assertEquals(expectedTrailerSignature.decodeToString(), trailerSignature)

        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }
}
