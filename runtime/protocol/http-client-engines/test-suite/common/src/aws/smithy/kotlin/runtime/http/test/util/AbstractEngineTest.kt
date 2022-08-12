/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.Url
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import aws.smithy.kotlin.runtime.io.use
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// TODO - add ipv6 test server/tests
private val TEST_SERVER = Url.parse("http://127.0.0.1:8082")

/**
 * Abstract base class that all engine test suite test classes should inherit from.
 */
public abstract class AbstractEngineTest {

    /**
     * Build a test that will run against each engine in the test suite.
     *
     * Concrete implementations for each KMP target are responsible for loading the engines
     * supported by that platform and executing the test
     */
    public fun testEngines(skipEngines: Set<String> = emptySet(), block: EngineTestBuilder.() -> Unit) {
        val builder = EngineTestBuilder().apply(block)
        engineFactories()
            .filter { it.name !in skipEngines }
            .forEach { engineFactory ->
                val engine = engineFactory.create(builder.engineConfig)
                sdkHttpClient(engine, manageEngine = true).use { client ->
                    testWithClient(client, builder = builder)
                }
            }
    }
}

/**
 * Concrete implementations for each KMP target are responsible for loading the engines
 * supported by that platform and executing the test
 */
internal expect fun engineFactories(): List<TestEngineFactory>

/**
 * Container for current engine test environment
 *
 * @param testServer the URL to the local running test server
 * @param coroutineId unique ID for current coroutine/job (concurrency > 1)
 * @param attempt the current attempt number when repeat > 1
 */
public data class TestEnvironment(public val testServer: Url, public val coroutineId: Int, public val attempt: Int)

/**
 * Configure the test
 */
public class EngineTestBuilder {
    /**
     * Lambda function invoked to configure the [HttpClientEngineConfig] to use for the test. If not specified
     * [HttpClientEngineConfig.Default] is used
     */
    public var engineConfig: HttpClientEngineConfig.Builder.() -> Unit = {}

    /**
     * Lambda function that is invoked with the current test environment and an [SdkHttpClient]
     * configured with an engine loaded by [AbstractEngineTest]. Invoke calls against test routes and make
     * assertions here. This will potentially be invoked multiple times (once for each engine supported by a platform).
     */
    public var test: (suspend (env: TestEnvironment, client: SdkHttpClient) -> Unit) =
        { _, _ -> error("engine test not configured") }

    /**
     * Number of times to repeat [test]
     */
    public var repeat: Int = 1

    /**
     * The number of coroutines to launch. Each coroutine will invoke [test]
     */
    public var concurrency: Int = 1
}

/**
 * Shared entry point usable by implementations of [AbstractEngineTest.testEngines]
 */
public fun testWithClient(
    client: SdkHttpClient,
    timeout: Duration = 60.seconds,
    builder: EngineTestBuilder,
): Unit = runBlockingTest(timeout = timeout) {
    runConcurrently(builder.concurrency) { coroutineId ->
        repeat(builder.repeat) { attempt ->
            val env = TestEnvironment(TEST_SERVER, coroutineId, attempt)
            builder.test(env, client)
        }
    }
}

// Use a real dispatcher rather than `runTest` (e.g. runBlocking for JVM/Native) which more appropriately matches
// a real environment
internal expect fun runBlockingTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration? = null,
    block: suspend CoroutineScope.() -> Unit,
): Unit

private suspend fun runConcurrently(level: Int, block: suspend (Int) -> Unit) {
    coroutineScope {
        List(level) {
            async {
                block(it)
            }
        }.awaitAll()
    }
}

public fun EngineTestBuilder.test(block: suspend (env: TestEnvironment, client: SdkHttpClient) -> Unit) {
    test = block
}

public fun HttpRequestBuilder.testSetup(env: TestEnvironment) {
    url(env.testServer)
    headers.append("Host", "${env.testServer.host}:${env.testServer.port}")
}

public fun EngineTestBuilder.engineConfig(block: HttpClientEngineConfig.Builder.() -> Unit) {
    engineConfig = block
}

internal data class TestEngineFactory(
    /**
     * Unique name for the engine
     */
    val name: String,
    /**
     * Configure a new [HttpClientEngine] instance and return it
     */
    val configure: (HttpClientEngineConfig.Builder.() -> Unit) -> HttpClientEngine,
) {
    fun create(block: HttpClientEngineConfig.Builder.() -> Unit): HttpClientEngine = configure(block)
}
