/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awssigning.*
import aws.smithy.kotlin.runtime.auth.awssigning.internal.CHUNK_SIZE_BYTES
import aws.smithy.kotlin.runtime.http.DeferredHeaders
import aws.smithy.kotlin.runtime.http.DeferredHeaders.Companion.toHeaders
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

interface AwsChunkedTestReader {
    // This may modify the chunked reader state and cause loss of data!
    fun isClosedForRead(): Boolean
    suspend fun read(sink: SdkBuffer, limit: Long): Long
}

fun interface AwsChunkedReaderFactory {
    companion object {
        val Channel = AwsChunkedReaderFactory { data, signer, signingConfig, previousSignature, trailingHeaders ->
            val ch = SdkByteReadChannel(data)
            val chunked = AwsChunkedByteReadChannel(ch, signer, signingConfig, previousSignature, trailingHeaders)
            object : AwsChunkedTestReader {
                override fun isClosedForRead(): Boolean = chunked.isClosedForRead
                override suspend fun read(sink: SdkBuffer, limit: Long): Long = chunked.read(sink, limit)
            }
        }
    }

    fun create(
        data: ByteArray,
        signer: AwsSigner,
        signingConfig: AwsSigningConfig,
        previousSignature: ByteArray,
        trailingHeaders: DeferredHeaders,
    ): AwsChunkedTestReader
}

fun AwsChunkedReaderFactory.create(
    data: ByteArray,
    signer: AwsSigner,
    signingConfig: AwsSigningConfig,
    previousSignature: ByteArray,
): AwsChunkedTestReader = create(data, signer, signingConfig, previousSignature, DeferredHeaders.Empty)

@OptIn(ExperimentalCoroutinesApi::class)
abstract class AwsChunkedTestBase(
    val factory: AwsChunkedReaderFactory,
) : HasSigner {
    val CHUNK_SIGNATURE_REGEX = Regex("chunk-signature=[a-zA-Z0-9]{64}") // alphanumeric, length of 64
    val CHUNK_SIZE_REGEX = Regex("[0-9a-f]+;chunk-signature=") // hexadecimal, any length, immediately followed by the chunk signature
    val UNSIGNED_CHUNK_SIZE_REGEX = Regex("[0-9a-f]+\r\n")

    val testChunkSigningConfig = AwsSigningConfig {
        region = "foo"
        service = "bar"
        signingDate = Instant.fromIso8601("20220427T012345Z")
        credentialsProvider = testCredentialsProvider
        signatureType = AwsSignatureType.HTTP_REQUEST_CHUNK
        hashSpecification = HashSpecification.CalculateFromPayload
    }

    val testTrailingHeadersSigningConfig = AwsSigningConfig {
        region = "foo"
        service = "bar"
        signingDate = Instant.fromIso8601("20220427T012345Z")
        credentialsProvider = testCredentialsProvider
        signatureType = AwsSignatureType.HTTP_REQUEST_TRAILING_HEADERS
        hashSpecification = HashSpecification.CalculateFromPayload
    }

    val testUnsignedChunkSigningConfig = AwsSigningConfig {
        region = "foo"
        service = "bar"
        signingDate = Instant.fromIso8601("20220427T012345Z")
        credentialsProvider = testCredentialsProvider
        signatureType = AwsSignatureType.HTTP_REQUEST_CHUNK
        hashSpecification = HashSpecification.StreamingUnsignedPayloadWithTrailers
    }

    /**
     * Given a string representation of aws-chunked encoded bytes, return a list of the chunk signatures as strings.
     * Chunk signatures are defined by the following grammar:
     * chunk-signature=<64 alphanumeric characters>
     */
    fun getChunkSignatures(bytes: String): List<String> = CHUNK_SIGNATURE_REGEX.findAll(bytes).map {
            result ->
        result.value.split("=")[1]
    }.toList()

    /**
     * Given a string representation of aws-chunked encoded bytes, returns a list of the chunk sizes as integers.
     * Chunk sizes are defined by the following grammar:
     * String(Hex(ChunkSize));chunk-signature=<chunk_signature>
     */
    fun getChunkSizes(bytes: String, isUnsignedChunk: Boolean = false): List<Int> =
        if (isUnsignedChunk) {
            UNSIGNED_CHUNK_SIZE_REGEX.findAll(bytes).map {
                    result ->
                result.value.split("\r\n")[0].toInt(16)
            }.toList()
        } else {
            CHUNK_SIZE_REGEX.findAll(bytes).map {
                    result ->
                result.value.split(";")[0].toInt(16)
            }.toList()
        }

    /**
     * Given a string representation of aws-chunked encoded bytes, return the value of the x-amz-trailer-signature trailing header.
     */
    fun getChunkTrailerSignature(bytes: String): String? {
        val re = Regex("x-amz-trailer-signature:[a-zA-Z0-9]{64}")
        return re.findAll(bytes).map { result ->
            result.value.split(":")[1]
        }.toList().firstOrNull()
    }

    /**
     * Calculates the `aws-chunked` encoded trailing header length
     * Used to calculate how many bytes should be read for all the trailing headers to be consumed
     */
    suspend fun getTrailingHeadersLength(trailingHeaders: DeferredHeaders, isUnsignedChunk: Boolean = false) = trailingHeaders.toHeaders().entries().map {
            entry ->
        buildString {
            append(entry.key)
            append(":")
            append(entry.value.joinToString(","))
            append("\r\n")
        }.length
    }.reduce { acc, len -> acc + len } +
        if (!isUnsignedChunk) "x-amz-trailer-signature:".length + 64 + "\r\n".length else 0

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
    fun encodedChunkLength(chunkSize: Int): Int {
        var length = chunkSize.toString(16).length +
            ";chunk-signature=".length +
            64 + // the chunk signature is always 64 bytes
            "\r\n".length

        if (chunkSize > 0) {
            length += chunkSize + "\r\n".length
        }

        return length
    }

    fun encodedUnsignedChunkLength(chunkSize: Int): Int {
        var length = chunkSize.toString(16).length + "\r\n".length
        if (chunkSize > 0) {
            length += chunkSize + "\r\n".length
        }
        return length
    }

    @Test
    fun testReadNegativeOffset(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = factory.create(data, signer, testChunkSigningConfig, previousSignature)

        val sink = SdkBuffer()
        assertFailsWith<IllegalArgumentException> {
            awsChunked.read(sink, -500)
        }
    }

    @Test
    fun testReadExactBytes(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = factory.create(data, signer, testChunkSigningConfig, previousSignature)

        // read (up to) all of the chunk data plus all bytes from header
        val readLimit = encodedChunkLength(dataLengthBytes) + encodedChunkLength(0) + "\r\n".length

        val sink = SdkBuffer()
        // need to make 2 successive calls because there are two chunks -- read will only fetch the first one due to limit
        var bytesRead = awsChunked.read(sink, readLimit.toLong())
        bytesRead += awsChunked.read(sink, readLimit - bytesRead)

        val bytesAsString = sink.readUtf8()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(2, chunkSignatures.size) // chunk of data plus an empty chunk
        var expectedChunkSignature = signer.signChunk(data, previousSignature, testChunkSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        expectedChunkSignature = signer.signChunk(byteArrayOf(), expectedChunkSignature, testChunkSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(2, chunkSizes.size)
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])
        assertEquals(0, chunkSizes[1])

        assertEquals(readLimit, bytesRead.toInt())
        assertTrue(awsChunked.isClosedForRead())
    }

    @Test
    fun testReadExcessiveBytes(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = factory.create(data, signer, testChunkSigningConfig, previousSignature)

        val readLimit = encodedChunkLength(dataLengthBytes * 2) + encodedChunkLength(0) + "\r\n".length
        val sink = SdkBuffer()
        var bytesRead = awsChunked.read(sink, readLimit.toLong())
        bytesRead += awsChunked.read(sink, readLimit.toLong())

        val bytesAsString = sink.readUtf8()
        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(2, chunkSignatures.size) // chunk of data plus an empty chunk
        var expectedChunkSignature = signer.signChunk(data, previousSignature, testChunkSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])
        expectedChunkSignature = signer.signChunk(byteArrayOf(), expectedChunkSignature, testChunkSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(2, chunkSizes.size)
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])
        assertEquals(0, chunkSizes[1])

        assertNotEquals(bytesRead, readLimit.toLong()) // because we requested more bytes than were available
        assertTrue(awsChunked.isClosedForRead())
    }

    @Test
    fun testReadFewerBytes(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val previousSignature: ByteArray = byteArrayOf()
        val awsChunked = factory.create(data, signer, testChunkSigningConfig, previousSignature)

        val readLimit = encodedChunkLength(dataLengthBytes / 2) + encodedChunkLength(0) + "\r\n".length

        val sink = SdkBuffer()
        val bytesRead = awsChunked.read(sink, readLimit.toLong())
        assertEquals(readLimit.toLong(), bytesRead)

        val bytesAsString = sink.readUtf8()
        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(1, chunkSignatures.size)
        val expectedChunkSignature = signer.signChunk(data, previousSignature, testChunkSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(1, chunkSizes.size)
        assertEquals(CHUNK_SIZE_BYTES, chunkSizes[0])

        assertFalse(awsChunked.isClosedForRead())
    }

    @Test
    fun testReadMultipleFullChunks(): TestResult = runTest {
        val numChunks = 5
        val dataLengthBytes = CHUNK_SIZE_BYTES * numChunks
        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = factory.create(data, signer, testChunkSigningConfig, previousSignature)

        val totalBytesExpected = encodedChunkLength(CHUNK_SIZE_BYTES) * numChunks + encodedChunkLength(0) + "\r\n".length
        val sink = SdkBuffer()

        var bytesRead = 0L
        val readLimit = CHUNK_SIZE_BYTES + (dataLengthBytes.toString(16).length + 1 + "chunk-signature=".length + 64 + 4)
        for (chunk in 0 until numChunks) { // read the chunks in a loop
            bytesRead += awsChunked.read(sink, readLimit.toLong())
        }
        bytesRead += awsChunked.read(sink, readLimit.toLong())
        assertEquals(totalBytesExpected.toLong(), bytesRead)

        val bytesAsString = sink.readUtf8()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(numChunks + 1, chunkSignatures.size)
        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(numChunks + 1, chunkSizes.size)

        // validate each chunk signature
        for (chunk in 0 until numChunks) {
            val expectedChunkSignature = signer.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testChunkSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
            assertEquals(CHUNK_SIZE_BYTES, chunkSizes[chunk])
        }

        // validate the terminal chunk
        val expectedChunkSignature = signer.signChunk(byteArrayOf(), previousSignature, testChunkSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
        assertEquals(0, chunkSizes.last())

        assertTrue(awsChunked.isClosedForRead())
    }

    @Test
    fun testReadMultipleChunksLastChunkNotFull(): TestResult = runTest {
        val numChunks = 6
        val dataLengthBytes = CHUNK_SIZE_BYTES * (numChunks - 1) + CHUNK_SIZE_BYTES / 2 // 5 full chunks, 1 half-full chunk

        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = factory.create(data, signer, testChunkSigningConfig, previousSignature)

        val totalBytesExpected = encodedChunkLength(CHUNK_SIZE_BYTES) * (numChunks - 1) +
            encodedChunkLength(CHUNK_SIZE_BYTES / 2) + encodedChunkLength(0) + "\r\n".length
        val sink = SdkBuffer()

        var bytesRead = 0L

        // Use small reads to exercise we aren't too dependent on exact read limit sizes
        val readLimit = 64L

        withTimeout(30.seconds) {
            while (true) {
                val rc = awsChunked.read(sink, readLimit)
                if (rc == -1L) break
                bytesRead += rc
            }
        }

        val bytesAsString = sink.readUtf8()

        assertEquals(totalBytesExpected.toLong(), bytesRead)
        assertTrue(awsChunked.isClosedForRead())

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(numChunks + 1, chunkSignatures.size)
        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(numChunks + 1, chunkSizes.size)

        // validate each chunk signature
        for (chunk in 0 until numChunks - 1) {
            val expectedChunkSignature = signer.signChunk(
                data.slice(CHUNK_SIZE_BYTES * chunk until (CHUNK_SIZE_BYTES * (chunk + 1))).toByteArray(),
                previousSignature,
                testChunkSigningConfig,
            ).signature
            previousSignature = expectedChunkSignature

            assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunk])
            assertEquals(CHUNK_SIZE_BYTES, chunkSizes[chunk])
        }

        // validate the last chunk
        var expectedChunkSignature = signer.signChunk(
            data.slice(CHUNK_SIZE_BYTES * (numChunks - 1) until data.size).toByteArray(),
            previousSignature,
            testChunkSigningConfig,
        ).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[chunkSignatures.size - 2])
        assertEquals(CHUNK_SIZE_BYTES / 2, chunkSizes[chunkSizes.size - 2])

        // validate terminal chunk
        expectedChunkSignature = signer.signChunk(byteArrayOf(), previousSignature, testChunkSigningConfig).signature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures.last())
        assertEquals(0, chunkSizes.last())
    }

    @Test
    fun testReadWithTrailingHeaders(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        var previousSignature: ByteArray = byteArrayOf()

        val trailingHeaders = DeferredHeaders {
            append("x-amz-checksum-crc32", CompletableDeferred("AAAAAA=="))
            append("x-amz-arbitrary-header-with-value", CompletableDeferred("UMM"))
        }

        val trailingHeadersLength = getTrailingHeadersLength(trailingHeaders)

        val awsChunked = factory.create(data, signer, testChunkSigningConfig, previousSignature, trailingHeaders)

        val totalBytesExpected = encodedChunkLength(CHUNK_SIZE_BYTES) + encodedChunkLength(0) + trailingHeadersLength + "\r\n".length
        val sink = SdkBuffer()

        var bytesRead = 0L

        while (bytesRead < totalBytesExpected.toLong()) {
            bytesRead += awsChunked.read(sink, Long.MAX_VALUE)
        }

        assertEquals(totalBytesExpected.toLong(), bytesRead)
        assertTrue(awsChunked.isClosedForRead())

        val bytesAsString = sink.readUtf8()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 2) // chunk of data plus an empty chunk
        var expectedChunkSignature = signer.signChunk(data, previousSignature, testChunkSigningConfig).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[0])

        // the second chunk signature should come from the chunk of zero length
        expectedChunkSignature = signer.signChunk(byteArrayOf(), previousSignature, testChunkSigningConfig).signature
        previousSignature = expectedChunkSignature
        assertEquals(expectedChunkSignature.decodeToString(), chunkSignatures[1])

        val chunkSizes = getChunkSizes(bytesAsString)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)

        val expectedTrailerSignature = signer.signChunkTrailer(trailingHeaders.toHeaders(), previousSignature, testTrailingHeadersSigningConfig).signature
        val trailerSignature = getChunkTrailerSignature(bytesAsString)
        assertEquals(expectedTrailerSignature.decodeToString(), trailerSignature)
    }

    @Test
    fun testUnsignedChunk(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val previousSignature: ByteArray = byteArrayOf()

        val awsChunked = factory.create(data, signer, testUnsignedChunkSigningConfig, previousSignature)

        val totalBytesExpected = encodedUnsignedChunkLength(CHUNK_SIZE_BYTES) + encodedUnsignedChunkLength(0) + "\r\n".length
        val sink = SdkBuffer()

        var bytesRead = 0L

        while (bytesRead < totalBytesExpected.toLong()) {
            bytesRead += awsChunked.read(sink, Long.MAX_VALUE)
        }

        assertEquals(totalBytesExpected.toLong(), bytesRead)
        assertTrue(awsChunked.isClosedForRead())

        val bytesAsString = sink.readUtf8()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 0) // unsigned chunk should have no signatures

        val chunkSizes = getChunkSizes(bytesAsString, isUnsignedChunk = true)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)
    }

    @Test
    fun testUnsignedChunkWithTrailingHeaders(): TestResult = runTest {
        val dataLengthBytes = CHUNK_SIZE_BYTES
        val data = ByteArray(dataLengthBytes) { 0x7A.toByte() }
        val previousSignature: ByteArray = byteArrayOf()

        val trailingHeaders = DeferredHeaders {
            append("x-amz-checksum-crc32", CompletableDeferred("AAAAAA=="))
            append("x-amz-arbitrary-header-with-value", CompletableDeferred("UMM"))
        }
        val trailingHeadersLength = getTrailingHeadersLength(trailingHeaders, isUnsignedChunk = true)

        val awsChunked = factory.create(data, signer, testUnsignedChunkSigningConfig, previousSignature, trailingHeaders)

        val totalBytesExpected = encodedUnsignedChunkLength(CHUNK_SIZE_BYTES) + encodedUnsignedChunkLength(0) + trailingHeadersLength + "\r\n".length
        val sink = SdkBuffer()

        var bytesRead = 0L

        while (bytesRead < totalBytesExpected.toLong()) {
            bytesRead += awsChunked.read(sink, Long.MAX_VALUE)
        }

        assertEquals(totalBytesExpected.toLong(), bytesRead)
        assertTrue(awsChunked.isClosedForRead())

        val bytesAsString = sink.readUtf8()

        val chunkSignatures = getChunkSignatures(bytesAsString)
        assertEquals(chunkSignatures.size, 0) // unsigned chunk should have no signatures

        val chunkSizes = getChunkSizes(bytesAsString, isUnsignedChunk = true)
        assertEquals(chunkSizes.size, 2)
        assertEquals(chunkSizes[0], CHUNK_SIZE_BYTES)
        assertEquals(chunkSizes[1], 0)

        assertNull(getChunkTrailerSignature(bytesAsString))
    }
}
