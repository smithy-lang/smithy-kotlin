/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HashingSourceTest {
    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testHashingSourceDigest(hashFunctionName: String) = run {
        val byteArray = ByteArray(19456) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(hashFunctionName.toHashFunction()!!, source)

        val sink = SdkBuffer()

        val expectedHash = hashFunctionName.toHashFunction()!!
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 19456)
        expectedHash.update(byteArray)

        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["crc32", "crc32c", "md5", "sha1", "sha256"])
    fun testHashingSourcePartialRead(hashFunctionName: String) = run {
        val byteArray = ByteArray(19456) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(hashFunctionName.toHashFunction()!!, source)

        val sink = SdkBuffer()

        val expectedHash = hashFunctionName.toHashFunction()!!
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 512)
        expectedHash.update(byteArray, 0, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 512)
        expectedHash.update(byteArray, 512, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())
    }
}
