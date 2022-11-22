/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.writeAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HttpBodyTest {
    @Test
    fun testFromByteStreamBytes() {
        val body = ByteStream.fromString("foobar").toHttpBody()
        assertIs<HttpBody.Bytes>(body)
    }

    @Test
    fun testFromByteStreamReplayable() {
        val stream = object : ByteStream.ChannelStream() {
            override val contentLength: Long = 6
            override val isOneShot: Boolean = false
            override fun readFrom(): SdkByteReadChannel = SdkByteReadChannel("foobar".encodeToByteArray())
        }

        val body = stream.toHttpBody()
        assertIs<HttpBody.ChannelContent>(body)
        assertFalse(body.isOneShot)
    }

    @Test
    fun testFromByteStreamOneShot() {
        val stream = object : ByteStream.ChannelStream() {
            override val contentLength: Long = 6
            override fun readFrom(): SdkByteReadChannel = SdkByteReadChannel("foobar".encodeToByteArray())
        }

        val body = stream.toHttpBody()
        assertIs<HttpBody.ChannelContent>(body)
        assertTrue(body.isOneShot)
    }

    @Test
    fun testStreamingReadAllClosedForRead() = runTest {
        val expected = "foobar"
        val stream = object : ByteStream.ChannelStream() {
            override val contentLength = expected.length.toLong()
            override fun readFrom() = SdkByteReadChannel(expected.encodeToByteArray())
        }
        val body = stream.toHttpBody()

        assertEquals(expected, body.readAll()!!.decodeToString())
    }

    @Test
    fun testStreamingReadAllClosedForWrite() = runTest {
        val expected = "foobar"
        val chan = SdkByteChannel(true)
        val source = SdkBuffer().apply { writeUtf8(expected) }
        chan.writeAll(source)
        chan.close()
        assertTrue(chan.isClosedForWrite)
        assertFalse(chan.isClosedForRead)

        val stream = object : ByteStream.ChannelStream() {
            override val contentLength = expected.length.toLong()
            override fun readFrom() = chan
        }
        val body = stream.toHttpBody()

        assertEquals(expected, body.readAll()!!.decodeToString())
    }
}
