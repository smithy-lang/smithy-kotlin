/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.benchmarks.http

import aws.smithy.kotlin.runtime.http.Protocol
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngine
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import kotlinx.benchmark.Blackhole
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.annotations.Level
import java.util.concurrent.TimeUnit

private const val CONCURRENT_CALLS = 50

// TODO - add TLS overhead
private const val OKHTTP_ENGINE = "OkHttp"
private const val CRT_ENGINE = "CRT"

fun interface BenchmarkEngineFactory {
    fun create(): HttpClientEngine
}

private val engines = mapOf<String, BenchmarkEngineFactory>(
    OKHTTP_ENGINE to BenchmarkEngineFactory { OkHttpEngine() },
    CRT_ENGINE to BenchmarkEngineFactory { CrtHttpEngine() }
)

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
open class HttpEngineBenchmarks {

    lateinit var httpClient: SdkHttpClient

    @Param(OKHTTP_ENGINE, CRT_ENGINE)
    var httpClientName: String = ""

    val request = HttpRequest {
        url {
            scheme = Protocol.HTTP
            host = "localhost"
            port = 8090
            path = "/hello"
        }

        headers {
            append("Host", url.host)
        }
    }

    @Setup(Level.Trial)
    fun create() {
        val engine = engines[httpClientName]!!.create()
        httpClient = sdkHttpClient(engine, manageEngine = true)
    }

    @TearDown(Level.Trial)
    fun destroy() {
        println("closing client")
        httpClient.close()
        // give time to background threads to complete asynchronous shutdown
        Thread.sleep(4000)
        println("destroy existing")
    }


    /**
     * Sequential requests raw throughput
     */
    @Benchmark
    fun roundTripSequentialNoTls(blackhole: Blackhole) = runBlocking {
        val call = httpClient.call(request)
        try {
            val body = call.response.body.readAll()
            blackhole.consume(body)
        }catch (ex: Exception) {
            println(ex)
        }finally {
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
                val call = httpClient.call(request)
                try {
                    val body = call.response.body.readAll()
                    blackhole.consume(body)
                }catch (ex: Exception) {
                    println("failed to consume body: ${ex.message}")
                    println("stacktrace: ${ex.stackTraceToString()}")
                }finally {
                    call.complete()
                }
            }
        }
    }
}

// TODO - sequential/concurrent requests internal buffering overhead (aka large response)