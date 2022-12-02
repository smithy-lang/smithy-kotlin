/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSink
import kotlin.test.Test
import kotlin.test.assertEquals

class HashingSinkTest {

    @Test
    fun testHashingSinkDigest() = run {
        val byteArray = ByteArray(1024) { 0xf }
        val buffer = SdkBuffer()
        buffer.write(byteArray)

        val hashingSink = HashingSink(Crc32c(), SdkSink.blackhole())

        val expectedHash = Crc32c()

        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
        hashingSink.write(buffer, buffer.size)
        expectedHash.update(byteArray)
        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
    }

    @Test
    fun testCrcSinkDigest() = run {
        val byteArray = ByteArray(1024) { 0xf }
        val buffer = SdkBuffer()
        buffer.write(byteArray)

        val hashingSink = HashingSink(Crc32(), SdkSink.blackhole())

        val expectedHash = Crc32()

        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
        hashingSink.write(buffer, buffer.size)
        expectedHash.update(byteArray)
        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
    }

    @Test
    fun testCrcSinkDigestValue() = run {
        val byteArray = ByteArray(1024) { 0xf }
        val buffer = SdkBuffer()
        buffer.write(byteArray)

        val hashingSink = HashingSink(Crc32(), SdkSink.blackhole())

        val expectedHash = Crc32()

        assertEquals(expectedHash.digestValue(), (hashingSink.hash as Crc32).digestValue())
        hashingSink.write(buffer, buffer.size)
        expectedHash.update(byteArray)
        assertEquals(expectedHash.digestValue(), (hashingSink.hash as Crc32).digestValue())
    }

    @Test
    fun testHashingSinkPartialWrite() = run {
        val byteArray = ByteArray(1024) { 0xf }
        val buffer = SdkBuffer()
        buffer.write(byteArray)

        val hashingSink = HashingSink(Sha256(), SdkSink.blackhole())
        val expectedHash = Sha256()

        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
        hashingSink.write(buffer, 512)
        expectedHash.update(byteArray, 0, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())

        hashingSink.write(buffer, 512)
        expectedHash.update(byteArray, 512, 512)
        assertEquals(expectedHash.digest().decodeToString(), hashingSink.digest().decodeToString())
    }
}
