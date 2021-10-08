/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlin.test.*

class HttpBodyTest {
    @Test
    fun testFromByteStreamBytes() {
        val body = ByteStream.fromString("foobar").toHttpBody()
        assertIs<HttpBody.Bytes>(body)
    }

    @Test
    fun testFromByteStreamReplayable() {
        val stream = object : ByteStream.ReplayableStream() {
            override val contentLength: Long = 6
            override fun newReader(): SdkByteReadChannel = SdkByteReadChannel("foobar".encodeToByteArray())
        }

        val body = stream.toHttpBody()
        assertIs<HttpBody.Streaming>(body)
        assertTrue(body.isReplayable)
    }

    @Test
    fun testFromByteStreamOneShot() {
        val stream = object : ByteStream.OneShotStream() {
            override val contentLength: Long = 6
            override fun readFrom(): SdkByteReadChannel = SdkByteReadChannel("foobar".encodeToByteArray())
        }

        val body = stream.toHttpBody()
        assertIs<HttpBody.Streaming>(body)
        assertFalse(body.isReplayable)
        assertFailsWith<UnsupportedOperationException> {
            body.reset()
        }
    }

    @Test
    fun testReset() = runSuspendTest {
        val stream = object : ByteStream.ReplayableStream() {
            override val contentLength: Long = 6
            override fun newReader(): SdkByteReadChannel = SdkByteReadChannel("foobar".encodeToByteArray())
        }

        val body = stream.toHttpBody()
        assertIs<HttpBody.Streaming>(body)
        assertEquals("foobar", body.readAll()!!.decodeToString())
        body.reset()
        assertEquals("foobar", body.readAll()!!.decodeToString())
    }

    @Test
    fun testDeepCopyEmpty() {
        assertEquals(HttpBody.Empty, HttpBody.Empty.deepCopy())
    }

    @Test
    fun testDeepCopyBytes() {
        val mutableBytes = byteArrayOf(1, 2, 3, 4, 5)
        val body1 = ByteStream.fromBytes(mutableBytes).toHttpBody() as HttpBody.Bytes

        val body2 = body1.deepCopy()
        mutableBytes[0] = -1

        assertEquals(-1, body1.bytes()[0])
        assertEquals(1, body2.bytes()[0])
    }
}
