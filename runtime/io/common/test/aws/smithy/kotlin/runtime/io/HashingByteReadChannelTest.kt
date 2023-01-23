/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.toHashFunction
import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HashingByteReadChannelTest {

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testReadAll(hashFunctionName: String) = runTest {
        val data = ByteArray(1024) { Random.Default.nextBytes(1)[0] }

        val channel = SdkByteReadChannel(data)
        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel)

        val hash = hashFunctionName.toHashFunction()!!

        val sink = SdkBuffer()

        hashingChannel.readAll(sink)
        hash.update(data)

        assertContentEquals(hash.digest(), hashingChannel.digest())
        assertContentEquals(data, sink.readByteArray())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testReadToBuffer(hashFunctionName: String) = runTest {
        val data = ByteArray(16000) { Random.Default.nextBytes(1)[0] }

        val channel = SdkByteReadChannel(data, 0, data.size)
        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel)

        val hash = hashFunctionName.toHashFunction()!!

        val buffer = hashingChannel.readToBuffer()
        hash.update(data)

        assertContentEquals(hash.digest(), hashingChannel.digest())
        assertContentEquals(data, buffer.readToByteArray())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testReadFully(hashFunctionName: String) = runTest {
        val data = ByteArray(2048) { Random.Default.nextBytes(1)[0] }

        val channel = SdkByteReadChannel(data, 0, data.size)
        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel)

        val hash = hashFunctionName.toHashFunction()!!

        val buffer = SdkBuffer()
        hashingChannel.readFully(buffer, data.size.toLong())
        hash.update(data)

        assertContentEquals(hash.digest(), hashingChannel.digest())
        assertContentEquals(data, buffer.readToByteArray())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testReadRemaining(hashFunctionName: String) = runTest {
        val data = ByteArray(9000) { Random.Default.nextBytes(1)[0] }

        val channel = SdkByteReadChannel(data, 0, data.size)
        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel)

        val hash = hashFunctionName.toHashFunction()!!

        val buffer = SdkBuffer()
        hashingChannel.readRemaining(buffer)
        hash.update(data)

        assertContentEquals(hash.digest(), hashingChannel.digest())
        assertContentEquals(data, buffer.readToByteArray())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testRead(hashFunctionName: String) = runTest {
        val data = ByteArray(2000) { Random.Default.nextBytes(1)[0] }

        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, SdkByteReadChannel(data, 0, data.size))

        val hash = hashFunctionName.toHashFunction()!!

        val buffer = SdkBuffer()

        hashingChannel.read(buffer, 1000)
        hash.update(data, 0, 1000)
        assertContentEquals(hash.digest(), hashingChannel.digest())

        hashingChannel.read(buffer, 1000)
        hash.update(data, 1000, 1000)
        assertContentEquals(hash.digest(), hashingChannel.digest())
    }

    @Test
    fun testCompletableDeferred() = runTest {
        val hashFunctionName = "sha256"

        val byteArray = ByteArray(2143) { 0xf }
        val channel = SdkByteReadChannel(byteArray)
        val completableDeferred = CompletableDeferred<String>()
        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel, completableDeferred)

        hashingChannel.read(SdkBuffer(), 1L)
        assertFalse(completableDeferred.isCompleted)

        hashingChannel.readAll(SdkBuffer())

        val expectedHash = hashFunctionName.toHashFunction()!!
        expectedHash.update(byteArray)

        assertTrue(completableDeferred.isCompleted)
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.getCompleted())
    }

}
