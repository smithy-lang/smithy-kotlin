/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.toHashFunction
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HashingByteReadChannelTest {

    private val hashFunctionNames = listOf("crc32", "crc32c", "md5", "sha1", "sha256")

    @Test
    fun testReadAll() = runTest {
        hashFunctionNames.forEach { hashFunctionName ->
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
    }

    @Test
    fun testReadToBuffer() = runTest {
        hashFunctionNames.forEach { hashFunctionName ->
            val data = ByteArray(16000) { Random.Default.nextBytes(1)[0] }

            val channel = SdkByteReadChannel(data, 0, data.size)
            val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel)

            val hash = hashFunctionName.toHashFunction()!!

            val buffer = hashingChannel.readToBuffer()
            hash.update(data)

            assertContentEquals(hash.digest(), hashingChannel.digest())
            assertContentEquals(data, buffer.readToByteArray())
        }
    }

    @Test
    fun testReadFully() = runTest {
        hashFunctionNames.forEach { hashFunctionName ->
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
    }

    @Test
    fun testReadRemaining() = runTest {
        hashFunctionNames.forEach { hashFunctionName ->
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
    }

    @Test
    fun testRead() = runTest {
        hashFunctionNames.forEach { hashFunctionName ->
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
    }
}
