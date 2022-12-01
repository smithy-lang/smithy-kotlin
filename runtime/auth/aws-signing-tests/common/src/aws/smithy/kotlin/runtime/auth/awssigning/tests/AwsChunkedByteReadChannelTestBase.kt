/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awssigning.*
import aws.smithy.kotlin.runtime.auth.awssigning.internal.CHUNK_SIZE_BYTES
import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
abstract class AwsChunkedByteReadChannelTestBase : AwsChunkedTestBase(AwsChunkedReaderFactory.Channel) {
    @Test
    fun testSlowProducerMultipleChunksPartialLast(): TestResult = runTest {
        val numChunks = 6
        val dataLengthBytes = CHUNK_SIZE_BYTES * (numChunks - 1) + CHUNK_SIZE_BYTES / 2 // 5 full chunks, 1 half-full chunk

        val data = ByteArray(dataLengthBytes) { Random.Default.nextBytes(1)[0] }
        val chan = SdkByteChannel(true)
        var previousSignature: ByteArray = byteArrayOf()
        val awsChunked = AwsChunkedByteReadChannel(chan, signer, testChunkSigningConfig, previousSignature)

        // launch a coroutine and fill the channel slowly
        val writeJob = launch {
            var offset = 0
            var remaining = data.size
            while (remaining > 0) {
                val wc = minOf(22, remaining)
                chan.write(data, offset, wc)
                remaining -= wc
                offset += wc
                delay(1.milliseconds)
            }
        }

        writeJob.invokeOnCompletion { cause -> chan.close(cause) }

        val totalBytesExpected = encodedChunkLength(CHUNK_SIZE_BYTES) * (numChunks - 1) +
            encodedChunkLength(CHUNK_SIZE_BYTES / 2) + encodedChunkLength(0) + "\r\n".length
        val sink = SdkBuffer()

        val bytesRead = awsChunked.readAll(sink)
        writeJob.join()

        val bytesAsString = sink.readUtf8()

        assertEquals(totalBytesExpected.toLong(), bytesRead)
        assertTrue(awsChunked.isClosedForRead)

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
}
