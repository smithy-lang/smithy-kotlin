/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.source
import kotlin.test.Test
import kotlin.test.assertEquals

class HashingSourceTest {

    @Test
    fun testHashingSourceDigest() = run {
        val byteArray = ByteArray(1024) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(Crc32c(), source)

        val sink = SdkBuffer()

        val expectedHash = Crc32c()
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 1024)
        expectedHash.update(byteArray)

        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())
    }

    @Test
    fun testCrcSourceDigest() = run {
        val byteArray = ByteArray(1024) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(Crc32(), source)

        val sink = SdkBuffer()

        val expectedHash = Crc32()
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 1024)
        expectedHash.update(byteArray)

        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())
    }

    @Test
    fun testCrcSourceDigestValue() = run {
        val byteArray = ByteArray(1024) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(Crc32(), source)

        val sink = SdkBuffer()

        val expectedHash = Crc32()
        assertEquals(expectedHash.digestValue(), (hashingSource.hash as Crc32).digestValue())

        hashingSource.read(sink, 1024)
        expectedHash.update(byteArray)

        assertEquals(expectedHash.digestValue(), (hashingSource.hash as Crc32).digestValue())
    }

    @Test
    fun testHashingSourcePartialRead() = run {
        val byteArray = ByteArray(1024) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(Md5(), source)

        val sink = SdkBuffer()

        val expectedHash = Md5()
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 512)
        expectedHash.update(byteArray, 0, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())

        hashingSource.read(sink, 512)
        expectedHash.update(byteArray, 512, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSource.digest().decodeToString())
    }
}
