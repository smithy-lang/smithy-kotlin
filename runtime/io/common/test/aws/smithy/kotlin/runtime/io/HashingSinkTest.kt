/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class HashingSinkTest {
    private fun getHashFunction(name: String): HashFunction = when (name) {
        "crc32" -> Crc32()
        "crc32c" -> Crc32c()
        "md5" -> Md5()
        "sha1" -> Sha1()
        "sha256" -> Sha256()
        else -> throw RuntimeException("HashFunction $name is not supported")
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testHashingSinkDigest(hashFunctionName: String) = run {
        val byteArray = ByteArray(1024) { 0xf }
        val buffer = SdkBuffer()
        buffer.write(byteArray)

        val hashingSink = HashingSink(getHashFunction(hashFunctionName), SdkSink.blackhole())

        val expectedHash = getHashFunction(hashFunctionName)

        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
        hashingSink.write(buffer, buffer.size)
        expectedHash.update(byteArray)
        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testHashingSinkPartialWrite(hashFunctionName: String) = run {
        val byteArray = ByteArray(1024) { 0xf }
        val buffer = SdkBuffer()
        buffer.write(byteArray)

        val hashingSink = HashingSink(getHashFunction(hashFunctionName), SdkSink.blackhole())
        val expectedHash = getHashFunction(hashFunctionName)

        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
        hashingSink.write(buffer, 512)
        expectedHash.update(byteArray, 0, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())

        hashingSink.write(buffer, 512)
        expectedHash.update(byteArray, 512, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
    }
}
