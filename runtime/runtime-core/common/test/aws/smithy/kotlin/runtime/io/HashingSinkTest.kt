/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.toHashFunction
import kotlin.test.Test
import kotlin.test.assertEquals

class HashingSinkTest {

    private val hashFunctionNames = listOf("crc32", "crc32c", "md5", "sha1", "sha256")

    @Test
    fun testHashingSinkDigest() = run {
        hashFunctionNames.forEach { hashFunctionName ->
            val byteArray = ByteArray(19456) { 0xf }
            val buffer = SdkBuffer()
            buffer.write(byteArray)

            val hashingSink = HashingSink(hashFunctionName.toHashFunction()!!, SdkSink.blackhole())

            val expectedHash = hashFunctionName.toHashFunction()!!

            assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
            hashingSink.write(buffer, buffer.size)
            expectedHash.update(byteArray)
            assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
        }
    }

    @Test
    fun testHashingSinkPartialWrite() = run {
        hashFunctionNames.forEach { hashFunctionName ->
            val byteArray = ByteArray(19456) { 0xf }
            val buffer = SdkBuffer()
            buffer.write(byteArray)

            val hashingSink = HashingSink(hashFunctionName.toHashFunction()!!, SdkSink.blackhole())
            val expectedHash = hashFunctionName.toHashFunction()!!

            assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
            hashingSink.write(buffer, 512)
            expectedHash.update(byteArray, 0, 512)
            assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())

            hashingSink.write(buffer, 512)
            expectedHash.update(byteArray, 512, 512)
            assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
        }
    }
}
