/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.tests.*
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AwsChunkedTest {

    private val CHUNK_SIGNATURE_REGEX = Regex("chunk-signature=[a-zA-Z0-9]{64}") // alphanumeric, length of 64
    private val CHUNK_SIZE_BYTES = AbstractAwsChunked.CHUNK_SIZE_BYTES

    private val testSigner = DefaultAwsSigner
    private val testSigningConfig = AwsSigningConfig {
        region = "foo"
        service = "bar"
        signingDate = Instant.fromIso8601("20220427T012345Z")
        credentialsProvider = testCredentialsProvider
    }

    @Test
    fun testReadRemainingExactBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val bytes = awsChunked.readRemaining(CHUNK_SIZE_BYTES + 1024) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = bytes.decodeToString()

        assertEquals(dataLengthBytes.toString(16), bytesAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = bytesAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)
        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadRemainingFewerBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()

        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()

        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = CHUNK_SIZE_BYTES / 2 // read ~half of the chunk data
        val bytes = awsChunked.readRemaining(numBytesToRead)
        val bytesAsString = bytes.decodeToString()

        // header should still come at the front
        assertEquals(dataLengthBytes.toString(16), bytesAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = bytesAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)

        assertFalse { awsChunked.isClosedForRead }
        assertEquals(bytes.size, numBytesToRead)
    }

    @Test
    fun testReadRemainingExcessiveBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()

        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()

        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = 131072 // read double of the available chunk data
        val bytes = awsChunked.readRemaining(numBytesToRead)
        val bytesAsString = bytes.decodeToString()

        // validate chunk header
        assertEquals(dataLengthBytes.toString(16), bytesAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = bytesAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)

        // validate chunk body
        val bytesAfterHeaderAsString = bytesAsString.split("\r\n")[1]
        assertEquals(bytesAfterHeaderAsString, data.decodeToString())

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

        val numBytesToRead = dataLengthBytes + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * numChunks

        val bytes = awsChunked.readRemaining(numBytesToRead) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = bytes.decodeToString()

        assertEquals(bytes.size, numBytesToRead)
        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = CHUNK_SIGNATURE_REGEX.findAll(bytesAsString).map { result -> result.value.split("=")[1] }.toList()
        assertEquals(chunkSignatures.size, numChunks)

        // validate each chunk signature
        for (chunk in 0 until numChunks) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
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
            (4 + 1 + "chunk-signature=".length + 64 + 4) // last chunk

        val bytes = awsChunked.readRemaining(numBytesToRead) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = bytes.decodeToString()

        assertEquals(bytes.size, numBytesToRead)
        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = CHUNK_SIGNATURE_REGEX.findAll(bytesAsString).map { result -> result.value.split("=")[1] }.toList()
        assertEquals(chunkSignatures.size, numChunks)

        // validate each chunk signature
        for (chunk in 0 until numChunks - 1) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
        }

        // validate the last chunk
        val expectedChunkSignature = testSigner.signChunk(
            data.slice(CHUNK_SIZE_BYTES * (numChunks - 1) until data.size).toByteArray(),
            previousSignature,
            testSigningConfig,
        ).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
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
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = dataLengthBytes + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4

        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead)

        val sinkAsString = sink.decodeToString()
        assertEquals(dataLengthBytes.toString(16), sinkAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = sinkAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)
        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadFullyFewerBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes / 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4

        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead)

        val sinkAsString = sink.decodeToString()
        assertEquals(dataLengthBytes.toString(16), sinkAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = sinkAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)
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
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = dataLengthBytes + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4

        val sink = ByteArray(numBytesToRead) { 0 }
        val offset = 128
        awsChunked.readFully(sink, offset, numBytesToRead - offset)

        for (index in 0..127) { // make sure the default value has not been overridden for the first `offset` bytes
            assertEquals(0, sink.get(index))
        }

        val sinkAsString = sink.slice(128 until sink.size).toByteArray().decodeToString()
        assertEquals(dataLengthBytes.toString(16), sinkAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = sinkAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)

        assertFalse { awsChunked.isClosedForRead }
    }

    @Test
    fun testReadFullyOnAlreadyClosedChannel() = runTest {
        val dataLengthBytes = 0
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)
        val numBytesToRead = dataLengthBytes + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4
        val sink = ByteArray(numBytesToRead)

        assertTrue(chan.isClosedForRead)
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

        val numBytesToRead = dataLengthBytes + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * numChunks
        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = sink.decodeToString()

        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = CHUNK_SIGNATURE_REGEX.findAll(bytesAsString).map { result -> result.value.split("=")[1] }.toList()
        assertEquals(chunkSignatures.size, numChunks)

        // validate each chunk signature
        for (chunk in 0 until numChunks) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
        }
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
            ((CHUNK_SIZE_BYTES / 2).toString(16).length + 1 + "chunk-signature=".length + 64 + 4) // last chunk

        val sink = ByteArray(numBytesToRead)
        awsChunked.readFully(sink, 0, numBytesToRead) // read all the underlying data plus any chunk signature overhead
        val bytesAsString = sink.decodeToString()

        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = CHUNK_SIGNATURE_REGEX.findAll(bytesAsString).map { result -> result.value.split("=")[1] }.toList()
        assertEquals(chunkSignatures.size, numChunks)

        // validate each chunk signature
        for (chunk in 0 until numChunks - 1) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
        }

        // validate the last chunk
        val expectedChunkSignature = testSigner.signChunk(
            data.slice(CHUNK_SIZE_BYTES * (numChunks - 1) until data.size).toByteArray(),
            previousSignature,
            testSigningConfig,
        ).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
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
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        // read all the chunk data plus all bytes from header
        val numBytesToRead = dataLengthBytes + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4

        val sink = ByteArray(numBytesToRead)
        val bytesRead = awsChunked.readAvailable(sink, 0, numBytesToRead)

        val sinkAsString = sink.decodeToString()
        assertEquals(dataLengthBytes.toString(16), sinkAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = sinkAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)

        assertEquals(bytesRead, numBytesToRead)
        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadAvailableExcessiveBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)
        val numBytesToRead = dataLengthBytes * 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4
        val sink = ByteArray(numBytesToRead)

        val bytesRead = awsChunked.readAvailable(sink, 0, numBytesToRead)

        val sinkAsString = sink.decodeToString()
        assertEquals(dataLengthBytes.toString(16), sinkAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = sinkAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)

        assertNotEquals(bytesRead, numBytesToRead) // because we requested more bytes than were available
        assertTrue { awsChunked.isClosedForRead } // we've consumed all of the bytes
    }

    @Test
    fun testReadAvailableFewerBytes() = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val chan = SdkByteReadChannel(data)

        val previousSignature: ByteArray = byteArrayOf()
        val expectedChunkSignature = testSigner.signChunk(data, previousSignature, testSigningConfig).signature.decodeToString()
        val awsChunked = AwsChunked(chan, testSigner, testSigningConfig, previousSignature)

        val numBytesToRead = dataLengthBytes / 2 + dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4

        val sink = ByteArray(numBytesToRead)
        awsChunked.readAvailable(sink, 0, numBytesToRead)

        val sinkAsString = sink.decodeToString()
        assertEquals(dataLengthBytes.toString(16), sinkAsString.split(";")[0]) // hex-encoded length
        val chunkSignature = sinkAsString.split("chunk-signature=")[1].split("\r\n")[0] // chunk signature
        assertEquals(expectedChunkSignature, chunkSignature)
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

        val numBytesToRead = dataLengthBytes + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4) * numChunks
        val sink = ByteArray(numBytesToRead)

        var bytesRead = 0
        for (chunk in 0 until numChunks) { // read the chunks in a loop
            bytesRead += awsChunked.readAvailable(sink, bytesRead, CHUNK_SIZE_BYTES + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4))
        }

        val bytesAsString = sink.decodeToString()

        assertEquals(numBytesToRead, bytesRead)
        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = CHUNK_SIGNATURE_REGEX.findAll(bytesAsString).map { result -> result.value.split("=")[1] }.toList()
        assertEquals(chunkSignatures.size, numChunks)

        // validate each chunk signature
        for (chunk in 0 until numChunks) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
        }
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
            ((CHUNK_SIZE_BYTES / 2).toString(16).length + 1 + "chunk-signature=".length + 64 + 4) // last chunk

        val sink = ByteArray(numBytesToRead)

        var bytesRead = 0
        for (chunk in 0 until numChunks - 1) {
            bytesRead += awsChunked.readAvailable(sink, bytesRead, CHUNK_SIZE_BYTES + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4)) // read all the underlying data plus any chunk signature overhead
        }
        // read the last chunk, which is smaller in size
        bytesRead += awsChunked.readAvailable(sink, bytesRead, CHUNK_SIZE_BYTES / 2 + (CHUNK_SIZE_BYTES / 2).toString(16).length + 1 + "chunk-signature=".length + 64 + 4)

        val bytesAsString = sink.decodeToString()

        assertEquals(numBytesToRead, bytesRead)
        assertTrue { awsChunked.isClosedForRead }

        val chunkSignatures = CHUNK_SIGNATURE_REGEX.findAll(bytesAsString).map { result -> result.value.split("=")[1] }.toList()
        assertEquals(chunkSignatures.size, numChunks)

        // validate each chunk signature
        for (chunk in 0 until numChunks - 1) {
            val expectedChunkSignature = testSigner.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
        }

        // validate the last chunk
        val expectedChunkSignature = testSigner.signChunk(
            data.slice(CHUNK_SIZE_BYTES * (numChunks - 1) until data.size).toByteArray(),
            previousSignature,
            testSigningConfig,
        ).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
    }
}
