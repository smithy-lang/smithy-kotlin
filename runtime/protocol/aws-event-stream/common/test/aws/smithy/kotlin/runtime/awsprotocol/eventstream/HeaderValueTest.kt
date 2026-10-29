/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.awsprotocol.eventstream

import aws.smithy.kotlin.runtime.PlannedRemoval
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Uuid
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid as KotlinUuid

class HeaderValueTest {

    @Test
    fun testExpectAs() {
        assertEquals(true, HeaderValue.Bool(true).expectBool())
        assertEquals(12.toByte(), HeaderValue.Byte(12u).expectByte())
        assertEquals(12.toShort(), HeaderValue.Int16(12).expectInt16())
        assertEquals(12, HeaderValue.Int32(12).expectInt32())
        assertEquals(12L, HeaderValue.Int64(12L).expectInt64())
        assertEquals("foo", HeaderValue.String("foo").expectString())
        assertContentEquals("foo".encodeToByteArray(), HeaderValue.ByteArray("foo".encodeToByteArray()).expectByteArray())
        val ts = Instant.now()
        assertEquals(ts, HeaderValue.Timestamp(ts).expectTimestamp())
        @Suppress("DEPRECATION")
        @OptIn(PlannedRemoval::class)
        val uuid = Uuid.random()
        @Suppress("DEPRECATION")
        @OptIn(PlannedRemoval::class)
        assertEquals(uuid, HeaderValue.Uuid(uuid).expectUuid())
        val uuid4 = KotlinUuid.random()
        assertEquals(uuid4, HeaderValue.Uuid4(uuid4).expectUuid4())

        assertFails {
            HeaderValue.Int32(12).expectString()
        }.message.shouldContain("expected HeaderValue.String, found: Int32(value=12)")
    }

    private fun roundTripValue(value: HeaderValue): HeaderValue {
        val buf = SdkBuffer()
        value.encode(buf)
        return HeaderValue.decode(buf)
    }

    // String value at the maximum allowed length (32767 bytes) encodes and round-trips unchanged.
    @Test
    fun testEncodeStringValueMaxLength() {
        val str = "a".repeat(MAX_HEADER_VALUE_BYTE_LEN) // ASCII -> 32767 bytes
        assertEquals(MAX_HEADER_VALUE_BYTE_LEN, str.encodeToByteArray().size)
        val decoded = roundTripValue(HeaderValue.String(str))
        assertEquals(HeaderValue.String(str), decoded)
    }

    // String value one byte over the limit (32768 bytes) is rejected at encode time.
    @Test
    fun testEncodeStringValueTooLong() {
        val str = "a".repeat(MAX_HEADER_VALUE_BYTE_LEN + 1) // 32768 bytes
        assertFailsWith<IllegalStateException> {
            HeaderValue.String(str).encode(SdkBuffer())
        }.message.shouldContain("HeaderValue String too long")
    }

    // String value well over the limit (65536 bytes) is also rejected.
    @Test
    fun testEncodeStringValueFarTooLong() {
        val str = "a".repeat(65_536)
        assertFailsWith<IllegalStateException> {
            HeaderValue.String(str).encode(SdkBuffer())
        }.message.shouldContain("HeaderValue String too long")
    }

    // ByteArray value at the maximum allowed length (32767 bytes) round-trips.
    @Test
    fun testEncodeByteArrayValueMaxLength() {
        val bytes = ByteArray(MAX_HEADER_VALUE_BYTE_LEN) { 0x61 }
        val decoded = roundTripValue(HeaderValue.ByteArray(bytes))
        assertEquals(HeaderValue.ByteArray(bytes), decoded)
    }

    // ByteArray value one byte over the limit (32768 bytes) is rejected.
    @Test
    fun testEncodeByteArrayValueTooLong() {
        val bytes = ByteArray(MAX_HEADER_VALUE_BYTE_LEN + 1) { 0x61 } // 32768 bytes
        assertFailsWith<IllegalStateException> {
            HeaderValue.ByteArray(bytes).encode(SdkBuffer())
        }.message.shouldContain("HeaderValue ByteArray too long")
    }

    // ByteArray value well over the limit (65536 bytes) is also rejected.
    @Test
    fun testEncodeByteArrayValueFarTooLong() {
        val bytes = ByteArray(65_536) { 0x61 }
        assertFailsWith<IllegalStateException> {
            HeaderValue.ByteArray(bytes).encode(SdkBuffer())
        }.message.shouldContain("HeaderValue ByteArray too long")
    }

    // The limit is on the UTF-8 byte length, not the character count. A multibyte string with an
    // in-range character count but an out-of-range byte length must be rejected, and one that lands
    // exactly on the byte limit must be accepted.
    @Test
    fun testEncodeMultibyteStringByteLengthOverLimit() {
        // "é" is 2 UTF-8 bytes; 16384 chars -> 32768 bytes (one over the limit) though only 16384 chars.
        val str = "\u00e9".repeat(16_384)
        assertEquals(MAX_HEADER_VALUE_BYTE_LEN + 1, str.encodeToByteArray().size)
        assertFailsWith<IllegalStateException> {
            HeaderValue.String(str).encode(SdkBuffer())
        }.message.shouldContain("HeaderValue String too long")
    }

    @Test
    fun testEncodeMultibyteStringByteLengthAtLimit() {
        // "€" is 3 UTF-8 bytes; 10922 chars -> 32766 bytes, + 1 ASCII "a" -> 32767 bytes (the limit).
        val str = "\u20ac".repeat(10_922) + "a"
        assertEquals(MAX_HEADER_VALUE_BYTE_LEN, str.encodeToByteArray().size)
        val decoded = roundTripValue(HeaderValue.String(str))
        assertEquals(HeaderValue.String(str), decoded)
    }
}
