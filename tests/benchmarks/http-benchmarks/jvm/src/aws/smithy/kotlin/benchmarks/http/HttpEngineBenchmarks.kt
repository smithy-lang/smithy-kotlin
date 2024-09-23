/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.benchmarks.http

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.engine.CloseableHttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import aws.smithy.kotlin.runtime.http.engine.okhttp4.OkHttp4Engine
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.benchmark.Blackhole
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.annotations.Level
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

private const val CONCURRENT_CALLS = 50
private const val MB_PER_THROUGHPUT_OP = 12

// TODO - add TLS tests to benchmarks (or just move existing tests to use TLS since we expect that to be the norm)
private const val OKHTTP_ENGINE = "OkHttp"
private const val CRT_ENGINE = "CRT"
private const val OKHTTP4_ENGINE = "OkHttp4"

fun interface BenchmarkEngineFactory {
    fun create(): CloseableHttpClientEngine
}

private val engines = mapOf(
    OKHTTP_ENGINE to BenchmarkEngineFactory { OkHttpEngine() },
    CRT_ENGINE to BenchmarkEngineFactory { CrtHttpEngine() },
    OKHTTP4_ENGINE to BenchmarkEngineFactory { OkHttp4Engine() },
)

// 12MB
private val largeData = ByteArray(MB_PER_THROUGHPUT_OP * 1024 * 1024)

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
open class HttpEngineBenchmarks {
    @Param(OKHTTP_ENGINE, CRT_ENGINE, OKHTTP4_ENGINE)
    var httpClientName: String = ""

    lateinit var engine: CloseableHttpClientEngine
    lateinit var httpClient: SdkHttpClient

    private val serverPort: Int = ServerSocket(0).use { it.localPort }
    private val server = embeddedServer(Netty, port = serverPort) {
        routing {
            get("/hello") {
                call.respondText("hello")
            }
            get("/download") {
                call.response.header("x-foo", "foo")
                call.response.header("x-bar", "bar")
                call.response.header("x-baz", "baz")
                call.response.header("x-foobar", "foobar")
                call.respondBytes(largeData)
            }
            post("/upload") {
                val packet = call.request.receiveChannel().readRemaining()
                call.respondText("read ${packet.remaining} bytes")
                packet.close()
            }
        }
    }

    private val helloRequest = HttpRequest {
        url {
            scheme = Scheme.HTTP
            host = Host.Domain("localhost")
            port = serverPort
            path.decoded = "/hello"
        }

        headers {
            append("Host", url.host.toString())
        }
    }

    private val downloadRequest = HttpRequest {
        url {
            scheme = Scheme.HTTP
            host = Host.Domain("localhost")
            port = serverPort
            path.decoded = "/download"
        }

        headers {
            append("Host", url.host.toString())
        }
    }

    private val uploadRequestInMemoryBody = HttpRequest {
        url {
            scheme = Scheme.HTTP
            method = HttpMethod.POST
            host = Host.Domain("localhost")
            port = serverPort
            path.decoded = "/upload"
        }
        body = HttpBody.fromBytes(largeData)
    }

    // creates a new streaming body
    private fun uploadRequestStreamingBody(useSource: Boolean = false) = HttpRequest {
        url {
            scheme = Scheme.HTTP
            method = HttpMethod.POST
            host = Host.Domain("localhost")
            port = serverPort
            path.decoded = "/upload"
        }
        body = if (useSource) {
            object : HttpBody.SourceContent() {
                override val contentLength: Long = largeData.size.toLong()
                override fun readFrom(): SdkSource = largeData.source()
            }
        } else {
            object : HttpBody.ChannelContent() {
                override val contentLength: Long = largeData.size.toLong()
                private val ch = SdkByteReadChannel(largeData)
                override fun readFrom(): SdkByteReadChannel = ch
            }
        }
    }

    @Setup(Level.Trial)
    fun create() {
        println("benchmark test server listening on: localhost:$serverPort")
        server.start(false)
        engine = engines[httpClientName]!!.create()
        httpClient = SdkHttpClient(engine)
    }

    @TearDown(Level.Trial)
    fun destroy() {
        println("stopping server")
        server.stop(0, 0, TimeUnit.SECONDS)
        println("closing client")
        engine.close()
        // give time to background threads to complete asynchronous shutdown
        Thread.sleep(4000)
        println("destroy exiting")
    }

    /**
     * Sequential requests raw throughput
     */
    @Benchmark
    fun roundTripSequentialNoTls(blackhole: Blackhole) = runBlocking {
        val call = httpClient.call(helloRequest)
        try {
            val body = call.response.body.readAll()
            blackhole.consume(body)
        } catch (ex: Exception) {
            println(ex)
        } finally {
            call.complete()
        }
    }

    /**
     * Concurrent requests raw throughput
     */
    @Benchmark
    @OperationsPerInvocation(CONCURRENT_CALLS)
    fun roundTripConcurrentNoTls(blackhole: Blackhole) = runBlocking {
        repeat(CONCURRENT_CALLS) {
            // scope should wait for all children to complete
            launch {
                val call = httpClient.call(helloRequest)
                try {
                    val body = call.response.body.readAll()
                    blackhole.consume(body)
                } catch (ex: Exception) {
                    println("failed to consume body: ${ex.message}")
                } finally {
                    call.complete()
                }
            }
        }
    }

    /**
     * Raw download throughput (output MB/s will be roughly op/sec * MB/op)
     */
    @Benchmark
    @OperationsPerInvocation(MB_PER_THROUGHPUT_OP)
    fun downloadThroughputNoTls(blackhole: Blackhole) = runBlocking {
        val call = httpClient.call(downloadRequest)
        try {
            val body = call.response.body.readAll()
            blackhole.consume(body)
        } catch (ex: Exception) {
            println("failed to consume body: ${ex.message}")
        } finally {
            call.complete()
        }
    }

    /**
     * Raw upload throughput in-memory body (output MB/s will be roughly op/sec * MB/op)
     */
    @Benchmark
    @OperationsPerInvocation(MB_PER_THROUGHPUT_OP)
    fun uploadThroughputNoTls(blackhole: Blackhole) = runBlocking {
        val call = httpClient.call(uploadRequestInMemoryBody)
        try {
            val body = call.response.body.readAll()
            blackhole.consume(body)
        } catch (ex: Exception) {
            println("failed to consume body: ${ex.message}")
        } finally {
            call.complete()
        }
    }

    /**
     * Raw upload throughput for a streaming body with SdkByteChannel content (output MB/s will be roughly op/sec * MB/op)
     */
    @Benchmark
    @OperationsPerInvocation(MB_PER_THROUGHPUT_OP)
    fun uploadThroughputChannelContentNoTls(blackhole: Blackhole) = runBlocking {
        val call = httpClient.call(uploadRequestStreamingBody())
        try {
            val body = call.response.body.readAll()
            blackhole.consume(body)
        } catch (ex: Exception) {
            println("failed to consume body: ${ex.message}")
        } finally {
            call.complete()
        }
    }

    /**
     * Raw upload throughput for a streaming body with SdkSource content (output MB/s will be roughly op/sec * MB/op)
     */
    @Benchmark
    @OperationsPerInvocation(MB_PER_THROUGHPUT_OP)
    fun uploadThroughputSourceContentNoTls(blackhole: Blackhole) = runBlocking {
        val call = httpClient.call(uploadRequestStreamingBody(useSource = true))
        try {
            val body = call.response.body.readAll()
            blackhole.consume(body)
        } catch (ex: Exception) {
            println("failed to consume body: ${ex.message}")
        } finally {
            call.complete()
        }
    }
}
