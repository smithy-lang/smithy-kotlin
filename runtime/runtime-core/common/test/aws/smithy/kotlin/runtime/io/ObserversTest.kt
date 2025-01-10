/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.SdkSinkObserver
import aws.smithy.kotlin.runtime.io.internal.SdkSourceObserver
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserversTest {
    @Test
    fun testSdkSourceObserver() {
        val source = SdkBuffer()
        source.writeUtf8("a")
        source.writeUtf8("b".repeat(8192))
        source.writeUtf8("c")

        val observer = object : SdkSourceObserver(source) {
            val content = StringBuilder()
            override fun observe(data: ByteArray, offset: Int, length: Int) {
                val read = data.decodeToString(offset, offset + length)
                content.append(read)
            }
        }

        val sink = SdkBuffer()
        observer.read(sink, 1)
        observer.read(sink, 8192)
        observer.read(sink, 1)
        assertEquals(sink.readUtf8(), observer.content.toString())
    }

    @Test
    fun testSdkSinkObserver() {
        val sink = SdkSink.blackhole()

        val observer = object : SdkSinkObserver(sink) {
            val content = StringBuilder()
            override fun observe(data: ByteArray, offset: Int, length: Int) {
                val read = data.decodeToString(offset, offset + length)
                content.append(read)
            }
        }

        val buffer = observer.buffer()
        buffer.writeUtf8("a")
        buffer.emit()
        buffer.writeUtf8("b".repeat(8192))
        buffer.emit()
        buffer.writeUtf8("c")
        buffer.emit()
        buffer.flush()

        val expected = "a" + "b".repeat(8192) + "c"
        assertEquals(expected, observer.content.toString())
    }
}
