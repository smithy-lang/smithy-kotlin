/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class HashingSourceTest {
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
    fun testHashingSourceDigest(hashFunctionName: String) = run {
        val byteArray = ByteArray(1024) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(getHashFunction(hashFunctionName), source)

        val sink = SdkBuffer()

        val expectedHash = getHashFunction(hashFunctionName)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 1024)
        expectedHash.update(byteArray)

        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testHashingSourcePartialRead(hashFunctionName: String) = run {
        val byteArray = ByteArray(1024) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(getHashFunction(hashFunctionName), source)

        val sink = SdkBuffer()

        val expectedHash = getHashFunction(hashFunctionName)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 512)
        expectedHash.update(byteArray, 0, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 512)
        expectedHash.update(byteArray, 512, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c"])
    fun testCrcSourceDigest(hashFunctionName: String) = run {
        val byteArray = ByteArray(1024) { 0xf }
        val source = byteArray.source()
        val hashingSource = CrcSource(source, getHashFunction(hashFunctionName) as Crc32Base)

        val sink = SdkBuffer()

        val expectedHash = getHashFunction(hashFunctionName)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 1024)
        expectedHash.update(byteArray)

        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c"])
    fun testCrcSourceDigestValue(hashFunctionName: String) = run {
        val byteArray = ByteArray(1024) { 0xf }
        val source = byteArray.source()
        val hashingSource = CrcSource(source, getHashFunction(hashFunctionName) as Crc32Base)

        val sink = SdkBuffer()

        val expectedHash = getHashFunction(hashFunctionName) as Crc32Base
        assertEquals(expectedHash.digestValue(), hashingSource.digestValue())

        hashingSource.read(sink, 1024)
        expectedHash.update(byteArray)

        assertEquals(expectedHash.digestValue(), hashingSource.digestValue())
    }
}
