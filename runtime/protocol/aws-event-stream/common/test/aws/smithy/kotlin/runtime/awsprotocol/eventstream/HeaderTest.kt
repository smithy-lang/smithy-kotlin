/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.awsprotocol.eventstream

import aws.smithy.kotlin.runtime.io.SdkBuffer
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeaderTest {
    @Test
    fun testDecodeHeaderNameLengthZero() {
        // name length = 0x00, should be rejected
        val buffer = sdkBufferOf(byteArrayFrom(0x00, 0x02))
        assertFailsWith<IllegalStateException> {
            Header.decode(buffer)
        }.message.shouldContain("Invalid header name length")
    }

    @Test
    fun testDecodeHeaderNameLengthInSignedRange() {
        // name length = 3, name = "foo", value type = TRUE (0x00)
        val buffer = sdkBufferOf(byteArrayFrom(0x03, 'f', 'o', 'o', 0x00))
        val header = Header.decode(buffer)
        assertEquals("foo", header.name)
        assertEquals(HeaderValue.Bool(true), header.value)
    }

    @Test
    fun testDecodeHeaderNameLengthAbove127() {
        // name length = 0x80 (128) — signed byte parsing would misinterpret as -128
        val nameLen = 128
        val nameBytes = ByteArray(nameLen) { 'a'.code.toByte() }
        val buf = SdkBuffer()
        buf.writeByte(nameLen.toByte()) // 0x80
        buf.write(nameBytes)
        buf.writeByte(HeaderType.TRUE.value) // value type = TRUE

        val header = Header.decode(buf)
        assertEquals("a".repeat(128), header.name)
        assertEquals(HeaderValue.Bool(true), header.value)
    }

    @Test
    fun testDecodeHeaderNameLength255() {
        // name length = 0xFF (255) — max uint8 value
        val nameLen = 255
        val nameBytes = ByteArray(nameLen) { 'b'.code.toByte() }
        val buf = SdkBuffer()
        buf.writeByte(nameLen.toByte()) // 0xFF
        buf.write(nameBytes)
        buf.writeByte(HeaderType.FALSE.value) // value type = FALSE

        val header = Header.decode(buf)
        assertEquals("b".repeat(255), header.name)
        assertEquals(HeaderValue.Bool(false), header.value)
    }

    @Test
    fun testDecodeHeaderNameLengthExceedsAvailableBytes() {
        // name length = 200 but only 3 bytes of name data available
        val buffer = sdkBufferOf(byteArrayFrom(0xC8, 'a', 'b', 'c'))
        assertFailsWith<IllegalStateException> {
            Header.decode(buffer)
        }.message.shouldContain("Not enough bytes to read header name")
    }

    @Test
    fun testEncodeHeaderNameLength255() {
        val header = Header("b".repeat(255), HeaderValue.Bool(true))
        val buf = SdkBuffer()
        header.encode(buf)
        val decoded = Header.decode(buf)
        assertEquals(header, decoded)
    }

    @Test
    fun testEncodeHeaderNameTooLong() {
        val header = Header("a".repeat(256), HeaderValue.Bool(true))
        val buf = SdkBuffer()
        assertFailsWith<IllegalStateException> {
            header.encode(buf)
        }.message.shouldContain("Header name length should be in the range [1, 255], got 256")
    }

    // The header-name length limit (255) is measured in UTF-8 bytes, not characters. A multibyte name
    // whose character count is within range but whose byte length exceeds 255 must be rejected.
    @Test
    fun testEncodeMultibyteHeaderNameByteLengthOverLimit() {
        // "é" is 2 UTF-8 bytes; 128 chars -> 256 bytes (one over the 255-byte limit).
        val name = "\u00e9".repeat(128)
        assertEquals(256, name.encodeToByteArray().size)
        val header = Header(name, HeaderValue.Bool(true))
        assertFailsWith<IllegalStateException> {
            header.encode(SdkBuffer())
        }.message.shouldContain("Header name length should be in the range [1, 255], got 256")
    }

    @Test
    fun testEncodeMultibyteHeaderNameByteLengthAtLimit() {
        // 127 "é" (2 bytes each) + 1 ASCII "a" -> 255 bytes exactly.
        val name = "\u00e9".repeat(127) + "a"
        assertEquals(255, name.encodeToByteArray().size)
        val header = Header(name, HeaderValue.Bool(true))
        val buf = SdkBuffer()
        header.encode(buf)
        val decoded = Header.decode(buf)
        assertEquals(header, decoded)
    }
}
