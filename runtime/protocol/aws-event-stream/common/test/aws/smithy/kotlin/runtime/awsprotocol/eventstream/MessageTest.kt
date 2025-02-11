/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.awsprotocol.eventstream

import aws.smithy.kotlin.runtime.io.EOFException
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Uuid
import io.kotest.matchers.string.shouldContain
import kotlin.test.*

fun sdkBufferOf(b: ByteArray): SdkBuffer = SdkBuffer().apply { write(b) }

fun byteArrayFrom(vararg bytes: Any): ByteArray {
    val buf = ByteArray(bytes.size)
    bytes.forEachIndexed { index, x ->
        buf[index] = when (x) {
            is Int -> x.toByte()
            is Char -> x.code.toByte()
            else -> error("$x must be Int or Char")
        }
    }
    return buf
}

fun validMessageWithAllHeaders(): ByteArray = byteArrayFrom(
    0x00, 0x00, 0x00, 0x96, 0x00, 0x00, 0x00, 0x7a, 0x8b, 0xb4, 0x95, 0xfb, 0x04, 0x74, 0x72, 0x75,
    0x65, 0x00, 0x05, 0x66, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32,
    0x05, 0x73, 0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07,
    0xa1, 0x20, 0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x00, 0x0b, 0xa4, 0x3b, 0x74, 0x00,
    0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79,
    0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72, 0x07, 0x00, 0x08, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x73,
    0x74, 0x72, 0x04, 0x74, 0x69, 0x6d, 0x65, 0x08, 0x00, 0x00, 0x00, 0x01, 0x2a, 0x05, 0xf2, 0x00,
    0x04, 0x75, 0x75, 0x69, 0x64, 0x09, 0xb7, 0x9b, 0xc9, 0x14, 0xde, 0x21, 0x4e, 0x13, 0xb8, 0xb2,
    0xbc, 0x47, 0xe8, 0x5b, 0x7f, 0x0b, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x70, 0x61, 0x79, 0x6c, 0x6f,
    0x61, 0x64, 0x01, 0xa0, 0x58, 0x60,
)

fun validMessageEmptyPayload(): ByteArray = byteArrayFrom(
    0x00, 0x00, 0x00, 0x1f, 0x00, 0x00, 0x00, 0x0f, 0x17, 0x2d, 0xc2, 0xab, 0x0b, 0x73, 0x6f, 0x6d,
    0x65, 0x2d, 0x68, 0x65, 0x61, 0x64, 0x65, 0x72, 0x03, 0x01, 0xf4, 0x93, 0x3c, 0x5c, 0xad,
)

fun validMessageNoHeaders(): ByteArray = byteArrayFrom(
    0x00, 0x00, 0x00, 0x24, 0x00, 0x00, 0x00, 0x00, 0x51, 0x63, 0x56, 0xad, 0x61, 0x6e, 0x6f, 0x74,
    0x68, 0x65, 0x72, 0x20, 0x74, 0x65, 0x73, 0x74, 0x20, 0x70, 0x61, 0x79, 0x6c, 0x6f, 0x61, 0x64,
    0x8d, 0xf8, 0x0e, 0x65,
)

class MessageTest {

    @Test
    fun testMessageNoHeaders() {
        // Test message taken from the CRT:
        // https://github.com/awslabs/aws-c-event-stream/blob/main/tests/message_deserializer_test.c
        val data = byteArrayFrom(
            0x00, 0x00, 0x00, 0x1D, 0x00, 0x00, 0x00, 0x00, 0xfd, 0x52, 0x8c, 0x5a, 0x7b, 0x27,
            0x66, 0x6f, 0x6f, 0x27, 0x3a, 0x27, 0x62, 0x61, 0x72, 0x27, 0x7d, 0xc3, 0x65, 0x39,
            0x36,
        )

        val buffer = sdkBufferOf(data)
        val actual = Message.decode(buffer)
        val expectedPayload = """{'foo':'bar'}"""
        assertEquals(expectedPayload, actual.payload.decodeToString())
    }

    @Test
    fun testMessageOneHeader() {
        // Test message taken from the CRT:
        // https://github.com/awslabs/aws-c-event-stream/blob/main/tests/message_deserializer_test.c
        val data = byteArrayFrom(
            0x00, 0x00, 0x00, 0x3D, 0x00, 0x00, 0x00, 0x20, 0x07, 0xFD, 0x83, 0x96, 0x0C, 'c',
            'o', 'n', 't', 'e', 'n', 't', '-', 't', 'y', 'p', 'e', 0x07, 0x00, 0x10,
            'a', 'p', 'p', 'l', 'i', 'c', 'a', 't', 'i', 'o', 'n', '/', 'j', 's',
            'o', 'n', 0x7b, 0x27, 0x66, 0x6f, 0x6f, 0x27, 0x3a, 0x27, 0x62, 0x61, 0x72, 0x27,
            0x7d, 0x8D, 0x9C, 0x08, 0xB1,
        )

        val buffer = sdkBufferOf(data)
        val actual = Message.decode(buffer)
        val expectedPayload = """{'foo':'bar'}"""
        assertEquals(expectedPayload, actual.payload.decodeToString())

        val expectedHeaders = listOf(Header("content-type", HeaderValue.String("application/json")))
        assertEquals(expectedHeaders, actual.headers)
    }

    @Test
    fun testRoundTripAllHeadersPayload() {
        val encoded = validMessageWithAllHeaders()

        val message = buildMessage {
            payload = "some payload".encodeToByteArray()
            addHeader("true", HeaderValue.Bool(true))
            addHeader("false", HeaderValue.Bool(false))
            addHeader("byte", HeaderValue.Byte(50.toUByte()))
            addHeader("short", HeaderValue.Int16(20_000))
            addHeader("int", HeaderValue.Int32(500_000))
            addHeader("long", HeaderValue.Int64(50_000_000_000L))
            addHeader("bytes", HeaderValue.ByteArray("some bytes".encodeToByteArray()))
            addHeader("str", HeaderValue.String("some str"))
            addHeader("time", HeaderValue.Timestamp(Instant.fromEpochSeconds(5_000_000, 0)))
            addHeader("uuid", HeaderValue.Uuid(Uuid(0xb79bc914de214e13u.toLong(), 0xb8b2bc47e85b7f0bu.toLong())))
        }

        val dest = SdkBuffer()
        message.encode(dest)
        val peek = dest.peek()

        assertContentEquals(encoded, peek.readByteArray())

        val result = Message.decode(dest)
        assertContentEquals(message.headers, result.headers)
        assertContentEquals(message.payload, result.payload)
    }

    @Test
    fun testInvalidHeaderStringValueLength() {
        // header length = -1
        val encoded = byteArrayFrom(
            0x00, 0x00, 0x00, 0x96, 0x00, 0x00, 0x00, 0x7a, 0x8b, 0xb4, 0x95, 0xfb, 0x04, 0x74, 0x72, 0x75,
            0x65, 0x00, 0x05, 0x66, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32,
            0x05, 0x73, 0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07,
            0xa1, 0x20, 0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x00, 0x0b, 0xa4, 0x3b, 0x74, 0x00,
            0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79,
            0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72, 0x07, 0xff, 0xff, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x73,
            0x74, 0x72, 0x04, 0x74, 0x69, 0x6d, 0x65, 0x08, 0x00, 0x00, 0x00, 0x01, 0x2a, 0x05, 0xf2, 0x00,
            0x04, 0x75, 0x75, 0x69, 0x64, 0x09, 0xb7, 0x9b, 0xc9, 0x14, 0xde, 0x21, 0x4e, 0x13, 0xb8, 0xb2,
            0xbc, 0x47, 0xe8, 0x5b, 0x7f, 0x0b, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x70, 0x61, 0x79, 0x6c, 0x6f,
            0x61, 0x64, 0x01, 0xa0, 0x58, 0x60,
        )
        val buffer = sdkBufferOf(encoded)
        assertFailsWith<IllegalStateException> {
            Message.decode(buffer)
        }.message.shouldContain("Invalid HeaderValue; type=STRING, len=65535")
    }

    @Test
    fun testInvalidHeaderStringLengthCutoff() {
        val encoded = byteArrayFrom(
            0x00, 0x00, 0x00, 0x5d, 0x00, 0x00, 0x00, 0x4d, 0xad, 0xcc, 0xe9, 0x3e, 0x04, 0x74, 0x72, 0x75,
            0x65, 0x00, 0x05, 0x66, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32,
            0x05, 0x73, 0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07,
            0xa1, 0x20, 0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x00, 0x0b, 0xa4, 0x3b, 0x74, 0x00,
            0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79,
            0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72, 0x07, 0xff, 0x00, 0x00, 0x00, 0x00,
        )
        val buffer = sdkBufferOf(encoded)
        assertFailsWith<EOFException> {
            Message.decode(buffer)
        }
    }

    @Test
    fun testInvalidHeaderValueType() {
        val encoded = byteArrayFrom(
            0x00, 0x00, 0x00, 0x96, 0x00, 0x00, 0x00, 0x7a, 0x8b, 0xb4, 0x95, 0xfb, 0x04, 0x74, 0x72, 0x75,
            0x65, 0x00, 0x05, 0x66, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x60, 0x32,
            0x05, 0x73, 0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07,
            0xa1, 0x20, 0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x00, 0x0b, 0xa4, 0x3b, 0x74, 0x00,
            0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79,
            0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72, 0x07, 0x00, 0x08, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x73,
            0x74, 0x72, 0x04, 0x74, 0x69, 0x6d, 0x65, 0x08, 0x00, 0x00, 0x00, 0x01, 0x2a, 0x05, 0xf2, 0x00,
            0x04, 0x75, 0x75, 0x69, 0x64, 0x09, 0xb7, 0x9b, 0xc9, 0x14, 0xde, 0x21, 0x4e, 0x13, 0xb8, 0xb2,
            0xbc, 0x47, 0xe8, 0x5b, 0x7f, 0x0b, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x70, 0x61, 0x79, 0x6c, 0x6f,
            0x61, 0x64, 0x01, 0xa0, 0x58, 0x60,
        )
        val buffer = sdkBufferOf(encoded)
        assertFailsWith<IllegalArgumentException> {
            Message.decode(buffer)
        }.message.shouldContain("Unknown HeaderType: 96")
    }

    @Test
    fun testInvalidHeaderNameLength() {
        val encoded = byteArrayFrom(
            0x00, 0x00, 0x00, 0x96, 0x00, 0x00, 0x00, 0x7a, 0x8b, 0xb4, 0x95, 0xfb, 0x04, 0x74, 0x72, 0x75,
            0x65, 0x00, 0x05, 0x66, 0x61, 0x6c, 0x73, 0x65, 0x01, 0xff, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32,
            0x05, 0x73, 0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07,
            0xa1, 0x20, 0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x00, 0x0b, 0xa4, 0x3b, 0x74, 0x00,
            0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79,
            0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72, 0x07, 0x00, 0x08, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x73,
            0x74, 0x72, 0x04, 0x74, 0x69, 0x6d, 0x65, 0x08, 0x00, 0x00, 0x00, 0x01, 0x2a, 0x05, 0xf2, 0x00,
            0x04, 0x75, 0x75, 0x69, 0x64, 0x09, 0xb7, 0x9b, 0xc9, 0x14, 0xde, 0x21, 0x4e, 0x13, 0xb8, 0xb2,
            0xbc, 0x47, 0xe8, 0x5b, 0x7f, 0x0b, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x70, 0x61, 0x79, 0x6c, 0x6f,
            0x61, 0x64, 0x01, 0xa0, 0x58, 0x60,
        )
        val buffer = sdkBufferOf(encoded)
        assertFailsWith<IllegalStateException> {
            Message.decode(buffer)
        }.message.shouldContain("Invalid header name length")
    }

    @Test
    fun testInvalidHeadersLength() {
        val encoded = byteArrayFrom(
            0x00, 0x00, 0x00, 0x59, 0x00, 0x00, 0x00, 0x4d, 0x58, 0x4c, 0x4f, 0xfe, 0x04, 0x74, 0x72, 0x75,
            0x65, 0x00, 0x05, 0x66, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32,
            0x05, 0x73, 0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07,
            0xa1, 0x20, 0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x00, 0x0b, 0xa4, 0x3b, 0x74, 0x00,
            0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79,
            0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72, 0x07, 0xff,
        )
        val buffer = sdkBufferOf(encoded)
        assertFailsWith<IllegalStateException> {
            Message.decode(buffer)
        }.message.shouldContain("Not enough bytes to read header name; needed: 3; remaining: 1")
    }

    @Test
    fun testInvalidPreludeChecksum() {
        val encoded = byteArrayFrom(
            0x00, 0x00, 0x00, 0x96, 0x00, 0x00, 0x00, 0x7a, 0xde, 0xad, 0xbe, 0xef, 0x04, 0x74, 0x72, 0x75,
            0x65, 0x00, 0x05, 0x66, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32,
            0x05, 0x73, 0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07,
            0xa1, 0x20, 0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x00, 0x0b, 0xa4, 0x3b, 0x74, 0x00,
            0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79,
            0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72, 0x07, 0x00, 0x08, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x73,
            0x74, 0x72, 0x04, 0x74, 0x69, 0x6d, 0x65, 0x08, 0x00, 0x00, 0x00, 0x01, 0x2a, 0x05, 0xf2, 0x00,
            0x04, 0x75, 0x75, 0x69, 0x64, 0x09, 0xb7, 0x9b, 0xc9, 0x14, 0xde, 0x21, 0x4e, 0x13, 0xb8, 0xb2,
            0xbc, 0x47, 0xe8, 0x5b, 0x7f, 0x0b, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x70, 0x61, 0x79, 0x6c, 0x6f,
            0x61, 0x64, 0x01, 0xa0, 0x58, 0x60,
        )
        val buffer = sdkBufferOf(encoded)
        assertFailsWith<IllegalStateException> {
            Message.decode(buffer)
        }.message.shouldContain("Prelude checksum mismatch; expected=0xdeadbeef; calculated=0x8bb495fb")
    }

    @Test
    fun testInvalidMessageChecksum() {
        val encoded = byteArrayFrom(
            0x00, 0x00, 0x00, 0x96, 0x00, 0x00, 0x00, 0x7a, 0x8b, 0xb4, 0x95, 0xfb, 0x04, 0x74, 0x72, 0x75,
            0x65, 0x00, 0x05, 0x66, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32,
            0x05, 0x73, 0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07,
            0xa1, 0x20, 0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x00, 0x0b, 0xa4, 0x3b, 0x74, 0x00,
            0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79,
            0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72, 0x07, 0x00, 0x08, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x73,
            0x74, 0x72, 0x04, 0x74, 0x69, 0x6d, 0x65, 0x08, 0x00, 0x00, 0x00, 0x01, 0x2a, 0x05, 0xf2, 0x00,
            0x04, 0x75, 0x75, 0x69, 0x64, 0x09, 0xb7, 0x9b, 0xc9, 0x14, 0xde, 0x21, 0x4e, 0x13, 0xb8, 0xb2,
            0xbc, 0x47, 0xe8, 0x5b, 0x7f, 0x0b, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x70, 0x61, 0x79, 0x6c, 0x6f,
            0x61, 0x64, 0xde, 0xad, 0xbe, 0xef,
        )
        val buffer = sdkBufferOf(encoded)
        assertFailsWith<IllegalStateException> {
            Message.decode(buffer)
        }.message.shouldContain("Message checksum mismatch; expected=0xdeadbeef; calculated=0x01a05860")
    }

    @Test
    fun testInvalidHeaderNameLengthTooLong() {
        val encoded = byteArrayFrom(
            0x00, 0x00, 0x00, 0x5d, 0x00, 0x00, 0x00, 0x4d, 0xad, 0xcc, 0xe9, 0x3e, 0x04, 0x74, 0x72, 0x75,
            0x65, 0x00, 0x66, 0x05, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32,
            0x05, 0x73, 0x68, 0x5d, 0x0b, 0xa4, 0x3b, 0x74, 0x00, 0x05, 0x62, 0x79, 0x74, 0x65, 0x73, 0x06,
            0x00, 0x0a, 0x73, 0x6f, 0x6d, 0x65, 0x20, 0x62, 0x79, 0x74, 0x65, 0x73, 0x03, 0x73, 0x74, 0x72,
            0x07, 0x05, 0x61, 0x6c, 0x73, 0x65, 0x01, 0x04, 0x62, 0x79, 0x74, 0x65, 0x02, 0x32, 0x05, 0x73,
            0x68, 0x6f, 0x72, 0x74, 0x03, 0x4e, 0x20, 0x03, 0x69, 0x6e, 0x74, 0x04, 0x00, 0x07, 0xa1, 0x20,
            0x04, 0x6c, 0x6f, 0x6e, 0x67, 0x05, 0x00, 0x00, 0x5d, 0x0b, 0xa4, 0x3b, 0x74, 0x00, 0x05, 0x62,
            0x79, 0x74, 0x65, 0x73, 0x06, 0x00, 0xff, 0x00, 0x00, 0x00, 0x00,
        )
        val buffer = sdkBufferOf(encoded)
        assertFailsWith<IllegalStateException> {
            Message.decode(buffer)
        }.message.shouldContain("Not enough bytes to read header name; needed: 102; remaining: 70")
    }
}
