/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.benchmarks.channel

import aws.smithy.kotlin.runtime.io.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.annotations.Level
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

private const val MB_PER_THROUGHPUT_OP = 16
private const val SDK_CHANNEL_NAME = "Sdk"
private const val KTOR_CHANNEL_NAME = "Ktor"

private class TestSource(private val chunk: ByteArray) : SdkSource {
    private var remaining = chunk.size
    private var offset = 0
    override fun close() { }
    override fun read(sink: SdkBuffer, limit: Long): Long {
        if (remaining <= 0) return -1L
        val wc = minOf(remaining.toLong(), limit).toInt()
        sink.write(chunk, offset, wc)
        remaining -= wc
        offset += wc
        return wc.toLong()
    }
}

fun interface ChannelFactory {
    fun create(): TestChannel
}

interface TestChannel {
    // write the data as fast as possible
    suspend fun writeAll(data: ByteArray)

    // read data from the channel as fast as possible until nothing remains
    suspend fun consume()

    fun close()
}

private val channels = mapOf(
    SDK_CHANNEL_NAME to ChannelFactory {
        object : TestChannel {
            val ch = SdkByteChannel(true)
            override suspend fun consume() {
                ch.readAll(SdkSink.blackhole())
            }

            override suspend fun writeAll(data: ByteArray) {
                ch.writeAll(TestSource(data))
            }
            override fun close() {
                ch.close()
            }
        }
    },
    KTOR_CHANNEL_NAME to ChannelFactory {
        object : TestChannel {
            val ch = ByteChannel(true)
            override suspend fun writeAll(data: ByteArray) {
                ch.writeFully(data, 0, data.size)
            }

            override suspend fun consume() {
                val chunk = ByteBuffer.allocate(8192)
                while (!ch.isClosedForRead) {
                    ch.readAvailable(chunk)
                    chunk.clear()
                }
            }

            override fun close() {
                ch.close()
            }
        }
    },
)

private val data = ByteArray(1024 * 1024 * MB_PER_THROUGHPUT_OP) { it.toByte() }

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
open class ChannelBenchmarks {
    private lateinit var job: Job

    @Param(SDK_CHANNEL_NAME, KTOR_CHANNEL_NAME)
    var channelName: String = ""

    // private val ch = SdkByteChannel(true)
    private lateinit var ch: TestChannel

    @OptIn(DelicateCoroutinesApi::class)
    @Setup(Level.Trial)
    fun create() {
        ch = channels[channelName]!!.create()
        job = GlobalScope.launch {
            ch.consume()
        }
    }

    @TearDown(Level.Trial)
    fun destroy() {
        ch.close()
        runBlocking {
            job.join()
        }
    }

    /**
     * Raw throughput (MB/s will be roughly op/sec * MB/op)
     */
    @Benchmark
    fun benchmarkThroughput() = runBlocking {
        ch.writeAll(data)
    }
}
