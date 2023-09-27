/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import aws.sdk.kotlin.crt.io.MutableBuffer
import aws.smithy.kotlin.runtime.crt.SdkSourceBodyStream
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdkSourceBodyStreamTest {
    private fun mutableBuffer(capacity: Int): Pair<MutableBuffer, ByteArray> {
        val dest = ByteArray(capacity)
        return MutableBuffer.of(dest) to dest
    }

    @Test
    fun testReadFully() = runTest {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val source = data.source()
        val stream = SdkSourceBodyStream(source)

        val (sendBuffer, sent) = mutableBuffer(16)
        val streamDone = stream.sendRequestBody(sendBuffer)
        assertTrue(streamDone)
        assertTrue {
            sent.sliceArray(data.indices).contentEquals(data)
        }
    }

    @Test
    fun testPartialRead() = runTest {
        val source = "123456".encodeToByteArray().source()
        val stream = SdkSourceBodyStream(source)

        val (sendBuffer1, sent1) = mutableBuffer(3)
        var streamDone = stream.sendRequestBody(sendBuffer1)
        assertFalse(streamDone)
        assertEquals("123", sent1.decodeToString())
        assertEquals(0, sendBuffer1.writeRemaining)

        val (sendBuffer2, sent2) = mutableBuffer(3)
        streamDone = stream.sendRequestBody(sendBuffer2)
        assertTrue(streamDone)
        assertEquals("456", sent2.decodeToString())
    }

    @Test
    fun testLargeTransfer() = runTest {
        val data = "foobar"
        val n = 10_000
        val source = object : SdkSource {
            var count = n
            override fun close() {}
            override fun read(sink: SdkBuffer, limit: Long): Long {
                if (count <= 0) return -1L
                sink.writeUtf8(data)
                count--
                return data.length.toLong()
            }
        }

        val stream = SdkSourceBodyStream(source)

        var totalBytesRead = 0
        val sendSize = 16
        do {
            val (sendBuffer, _) = mutableBuffer(sendSize)
            val streamDone = stream.sendRequestBody(sendBuffer)
            totalBytesRead += sendSize - sendBuffer.writeRemaining
        } while (!streamDone)

        val expected = data.length * n
        assertEquals(expected, totalBytesRead)
    }
}
