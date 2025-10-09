/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import kotlinx.coroutines.test.runTest
import okhttp3.coroutines.executeAsync
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertFailsWith

class OkHttpEngineConfigTest {
    @Test
    fun testExecutorService() = runTest {
        val config = OkHttpEngineConfig {
            executorService = DummyExecutorService
        }

        val metrics = HttpClientMetrics("foo", TelemetryProvider.None)
        val engine = config.buildClient(metrics)

        val data = "a".repeat(100)
        val url = Url.parse("https://aws.amazon.com")
        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, ByteStream.fromString(data).toHttpBody())
        val okRequest = request.toOkHttpRequest(ExecutionContext(), coroutineContext, metrics)

        assertFailsWith<DummyExecutorException> {
            engine.newCall(okRequest).executeAsync()
        }
    }

    private object DummyExecutorService : ExecutorService by Executors.newSingleThreadExecutor() {
        override fun execute(command: Runnable?) = throw DummyExecutorException()
    }

    private class DummyExecutorException : Exception()
}
