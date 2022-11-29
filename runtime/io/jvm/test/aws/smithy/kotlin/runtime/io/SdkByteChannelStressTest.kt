/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class SdkByteChannelStressTest {

    @Test
    fun testWriteByteRaceCondition() = runBlocking {
        testReadWriteRaceCondition { it.writeByte(1) }
    }

    @Test
    fun testWriteIntRaceCondition() {
        testReadWriteRaceCondition { it.writeInt(1) }
    }

    val testBytes = ByteArray(1024 + 97) { it.toByte() }

    internal class TestSource(
        private val bytes: ByteArray,
    ) : SdkSource {
        private var closed = false
        override fun close() { closed = true }
        override fun read(sink: SdkBuffer, limit: Long): Long {
            if (closed) return -1L
            sink.write(bytes, 0, bytes.size)
            close()
            return bytes.size.toLong()
        }
    }

    @Test
    fun testWriteAllSourceCondition() {
        testReadWriteRaceCondition {
            val source = TestSource(testBytes)
            it.writeAll(source)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun testReadWriteRaceCondition(writer: (SdkBuffer) -> Unit): Unit = runBlocking {
        val channel = SdkByteChannel(false)

        val writeJob = GlobalScope.async {
            try {
                val sink = SdkBuffer()
                repeat(1_000_000) {
                    writer(sink)
                    channel.write(sink)
                    channel.flush()
                }
                channel.close()
            } catch (ex: Exception) {
                channel.close(ex)
                throw ex
            }
        }

        val readJob = GlobalScope.async {
            channel.readAll(SdkSink.blackhole())
        }

        withTimeout(60.seconds) {
            writeJob.await()
            readJob.await()
        }
    }

    @Test
    fun testReadAllSinkWriteAllSource() = runBlocking {
        val ch = SdkByteChannel(false)
        val source = TestSource(testBytes)
        val wc = ch.writeAll(source)
        ch.close()
        assertEquals(testBytes.size.toLong(), wc)

        val sink = SdkBuffer()
        val rc = ch.readAll(sink)
        assertEquals(testBytes.size.toLong(), rc)
        assertEquals(rc, sink.size)

        assertContentEquals(testBytes, sink.readByteArray())
    }

    @Test
    fun testReadAllFromFailedChannel() = runBlocking {
        val ch = SdkByteChannel(true)
        ch.cancel(TestException())
        assertFailsWith<TestException> {
            ch.readAll(SdkSink.blackhole())
        }
    }
}
