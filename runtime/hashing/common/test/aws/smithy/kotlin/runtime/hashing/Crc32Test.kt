/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {
    @Test
    fun testCrc32() {
        assertEquals(2666930069U, "foobar".encodeToByteArray().crc32())
    }

    @Test
    fun testBase64EncodedChecksumValue() {
        val crc = Crc32()
        val input = "foobar"

        crc.update(input.encodeToByteArray(), 0, input.length)
        assertEquals(2666930069U, crc.digestValue()) // checksum of "foobar"
        assertEquals("nvYflQ==", crc.digest().encodeBase64String())
    }

    @Test
    fun testReset() {
        val crc = Crc32()
        val input = "foobar"
        crc.update(input.encodeToByteArray(), 0, input.length)
        assertEquals(2666930069U, crc.digestValue()) // checksum of "foobar"
        crc.reset()
        assertEquals(0U, crc.digestValue())
    }

    @Test
    fun testChainedChecksum() {
        val crc = Crc32()
        val input = "foobar"
        crc.update(input.encodeToByteArray(), 0, input.length / 2)
        assertEquals(2356372769U, crc.digestValue()) // checksum of "foo"
        crc.update(input.encodeToByteArray(), (input.length - input.length / 2), input.length / 2)
        assertEquals(2666930069U, crc.digestValue()) // checksum of "foobar"
    }

    @Test
    fun testDigestResetsValue() {
        val crc = Crc32()
        val input = "foobar"
        crc.update(input.encodeToByteArray(), 0, input.length)
        assertEquals(2666930069U, crc.digestValue()) // checksum of "foobar"
        crc.digest()
        assertEquals(0U, crc.digestValue())
    }
}
