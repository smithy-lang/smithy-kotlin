/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32cTest {
    @Test
    fun testCrc32c() {
        assertEquals(224353407U, "foobar".encodeToByteArray().crc32c())
    }

    @Test
    fun testReset() {
        val crc = Crc32c()
        val input = "foobar"

        crc.update(input.encodeToByteArray(), 0, input.length)
        assertEquals(224353407U, crc.digestValue())

        crc.reset()
        assertEquals(0U, crc.digestValue()) // contents of crc should be zeroed out
    }

    @Test
    fun testChainedChecksum() {
        // run the checksum on both halves of the input with separate calls
        val crc = Crc32c()
        val input = "foobar"

        crc.update(input.encodeToByteArray(), 0, input.length / 2)
        assertEquals(3485773341U, crc.digestValue()) // checksum of "foo"

        crc.update(input.encodeToByteArray(), (input.length - input.length / 2), input.length / 2)
        assertEquals(224353407U, crc.digestValue()) // checksum of "foobar"
    }

    @Test
    fun testBase64EncodedChecksumValue() {
        val crc = Crc32c()
        val input = "foobar"

        crc.update(input.encodeToByteArray(), 0, input.length)
        assertEquals(224353407U, crc.digestValue()) // checksum of "foobar"
        assertEquals("DV9cfw==", crc.digest().encodeBase64String())
    }

    @Test
    fun testDigestResetsValue() {
        val crc = Crc32c()
        val input = "foobar"

        crc.update(input.encodeToByteArray(), 0, input.length)
        assertEquals(224353407U, crc.digestValue()) // checksum of "foobar"

        crc.digest()
        assertEquals(0U, crc.digestValue()) // checksum should be reset
    }

    @Test
    fun testNonAsciiInput() {
        val crc = Crc32c()
        val input = byteArrayOf(0)
        crc.update(input, 0, 1)
        val bytes = crc.digest()

        assertEquals("Un1TUQ==", bytes.encodeBase64String())
    }

    @Test
    fun testLargeInput() {
        val crc = Crc32c()
        val input = ByteArray(1024) { 0 }
        crc.update(input, 0, input.size)

        val bytes = crc.digest()
        assertEquals("7q7efA==", bytes.encodeBase64String())
    }

    @Test
    fun testNegativeByteInput() {
        val crc = Crc32c()
        val input = ByteArray(1024) { -1 }
        crc.update(input)

        val bytes = crc.digest()
        assertEquals("R+XN5A==", bytes.encodeBase64String())
    }
}
