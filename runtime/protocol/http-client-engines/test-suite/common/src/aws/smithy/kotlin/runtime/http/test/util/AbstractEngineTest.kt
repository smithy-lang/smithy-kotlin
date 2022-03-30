/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.Url
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.sdkHttpClient
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
abstract class AbstractEngineTest() {

    /**
     * Build a test that will run against each engine in the test suite.
     *
     * Concrete implementations for each KMP target are responsible for loading the engines
     * supported by that platform and executing the test
     */
    fun testEngines(block: EngineTestBuilder.() -> Unit) {
        engines().forEach { engine ->
            val client = sdkHttpClient(engine)
            testWithClient(client, block = block)
        }
    }
}

/**
 * Concrete implementations for each KMP target are responsible for loading the engines
 * supported by that platform and executing the test
 */
internal expect fun engines(): List<HttpClientEngine>

/**
 * Container for current engine test environment
 *
 * @param testServer the URL to the local running test server
 * @param coroutineId unique ID for current coroutine/job (concurrency > 1)
 * @param attempt the current attempt number when repeat > 1
 */
data class TestEnvironment(val testServer: Url, val coroutineId: Int, val attempt: Int)

/**
 * Configure the test
 */
class EngineTestBuilder {
    /**
     * Lambda function that is invoked with the current test environment and an [SdkHttpClient]
     * configured with an engine loaded by [AbstractEngineTest]. Invoke calls against test routes and make
     * assertions here
     */
    var test: (suspend (env: TestEnvironment, client: SdkHttpClient) -> Unit) = { _, _ -> error("engine test not configured") }

    /**
     * Number of times to repeat [test]
     */
    var repeat: Int = 1

    /**
     * The number of coroutines to launch. Each coroutine will invoke [test]
     */
    var concurrency: Int = 1
}

/**
 * Shared entry point usable by implementations of [AbstractEngineTest.testEngines]
 */
fun testWithClient(
    client: SdkHttpClient,
    timeout: Duration = 60.seconds,
    block: suspend EngineTestBuilder.() -> Unit
): Unit = runBlockingTest(timeout = timeout) {
    val builder = EngineTestBuilder().apply { block() }
    runConcurrently(builder.concurrency) { coroutineId ->
        repeat(builder.repeat) { attempt ->
            val env = TestEnvironment(TEST_SERVER, coroutineId, attempt)
            builder.test(env, client)
        }
    }
    client.close()
}

// Use a real dispatcher rather than `runTest` (e.g. runBlocking for JVM/Native) which more appropriately matches
// a real environment
internal expect fun runBlockingTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration? = null,
    block: suspend CoroutineScope.() -> Unit
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

fun EngineTestBuilder.test(block: suspend (env: TestEnvironment, client: SdkHttpClient) -> Unit) {
    test = block
}
