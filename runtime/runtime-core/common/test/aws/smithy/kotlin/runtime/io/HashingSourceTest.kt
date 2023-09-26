/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HashingSourceTest {

    private val hashFunctionNames = listOf("crc32", "crc32c", "md5", "sha1", "sha256")

    @Test
    fun testHashingSourceDigest() = run {
        hashFunctionNames.forEach { hashFunctionName ->
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
    }

    @Test
    fun testHashingSourcePartialRead() = run {
        hashFunctionNames.forEach { hashFunctionName ->
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
}
