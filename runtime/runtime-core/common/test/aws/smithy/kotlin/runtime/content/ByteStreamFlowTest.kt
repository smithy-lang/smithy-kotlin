/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

fun interface ByteStreamFactory {
    fun byteStream(input: ByteArray): ByteStream
    companion object {
        val BYTE_ARRAY: ByteStreamFactory = ByteStreamFactory { input -> ByteStream.fromBytes(input) }

        val SDK_SOURCE: ByteStreamFactory = ByteStreamFactory { input ->
            object : ByteStream.SourceStream() {
                override fun readFrom(): SdkSource = input.source()
                override val contentLength: Long = input.size.toLong()
            }
        }

        val SDK_CHANNEL: ByteStreamFactory = ByteStreamFactory { input ->
            object : ByteStream.ChannelStream() {
                override fun readFrom(): SdkByteReadChannel = SdkByteReadChannel(input)
                override val contentLength: Long = input.size.toLong()
            }
        }
    }
}

class ByteStreamBufferFlowTest : ByteStreamFlowTest(ByteStreamFactory.BYTE_ARRAY)
class ByteStreamSourceStreamFlowTest : ByteStreamFlowTest(ByteStreamFactory.SDK_SOURCE)
class ByteStreamChannelSourceFlowTest : ByteStreamFlowTest(ByteStreamFactory.SDK_CHANNEL)

abstract class ByteStreamFlowTest(
    private val factory: ByteStreamFactory,
) {
    @Test
    fun testToFlowWithSizeHint() = runTest {
        val data = "a korf is a tiger".repeat(1024).encodeToByteArray()
        val bufferSize = 8182 * 2
        val byteStream = factory.byteStream(data)
        val flow = byteStream.toFlow(bufferSize.toLong())
        val buffers = mutableListOf<ByteArray>()
        flow.toList(buffers)

        val totalCollected = buffers.fold(0) { acc, bytes -> acc + bytes.size }
        assertEquals(data.size, totalCollected)

        if (byteStream is ByteStream.Buffer) {
            assertEquals(1, buffers.size)
            assertContentEquals(data, buffers.first())
        } else {
            val expectedFullBuffers = data.size / bufferSize
            for (i in 0 until expectedFullBuffers) {
                val b = buffers[i]
                val expected = data.sliceArray((i * bufferSize)until (i * bufferSize + bufferSize))
                assertEquals(bufferSize, b.size)
                assertContentEquals(expected, b)
            }

            val last = buffers.last()
            val expected = data.sliceArray(((buffers.size - 1) * bufferSize) until data.size)
            assertContentEquals(expected, last)
        }
    }
}
