/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.telemetry.otel.OpenTelemetryProvider
import aws.smithy.kotlin.runtime.util.emptyAttributes
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.blackholeSink
import okio.buffer
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalApi::class)
class MetricsInterceptorTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private val provider = OpenTelemetryProvider(otelTesting.openTelemetry)
    private val meter = provider.meterProvider.getOrCreateMeter("test")

    @Test
    fun testInstrumentedSource() {
        val source = okio.Buffer()
        val data = "a".repeat(15 * 1024)
        source.writeUtf8(data)

        val sink = okio.Buffer()
        val counter = meter.createMonotonicCounter("TestCounter", "By")
        val instrumented = InstrumentedSource(source, counter, emptyAttributes())
        do {
            val rc = instrumented.read(sink, 399)
        } while (rc >= 0L)

        assertEquals(data.length.toLong(), sink.size)

        val counted = otelTesting.metrics.first().longCounterSum()
        assertEquals(data.length.toLong(), counted)
    }

    @Test
    fun testInstrumentedSink() {
        val source = okio.Buffer()
        val data = "b".repeat(13 * 1024)
        source.writeUtf8(data)

        val sink = okio.Buffer()
        val counter = meter.createMonotonicCounter("TestCounter", "By")
        val instrumented = InstrumentedSink(sink, counter, emptyAttributes())

        val buffered = instrumented.buffer()
        buffered.writeAll(source)
        buffered.close()

        assertEquals(data.length.toLong(), sink.size)

        val counted = otelTesting.metrics.first().longCounterSum()
        assertEquals(data.length.toLong(), counted)
    }

    @Test
    fun testMetricsInterceptor() {
        val reqData = "a".repeat(15 * 1024)
        val reqBody = reqData.toRequestBody()
        val metrics = HttpClientMetrics("test", provider)
        val tag = SdkRequestTag(ExecutionContext(), EmptyCoroutineContext, metrics)
        val request = Request.Builder()
            .url("https://localhost:1/")
            .method("PUT", reqBody)
            .tag<SdkRequestTag>(tag)
            .build()

        val respData = "b".repeat(13 * 1024)
        val respBody = respData.toResponseBody("text/plain; charset=utf-8".toMediaType())
        val mockResp = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("Intercepted")
            .body(respBody)
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(MetricsInterceptor)
            .addInterceptor { chain ->
                // consume the body and short circuit with a mock response
                chain.request().body?.writeTo(blackholeSink().buffer())
                mockResp
            }
            .build()

        val resp = client.newCall(request).execute()
        val actualRespData = resp.body.source().readByteArray().decodeToString()
        assertEquals(respData, actualRespData)

        val actualBytesSent = otelTesting.metrics
            .find { it.name == "smithy.client.http.bytes_sent" } ?: fail("expected bytes_sent")

        val actualBytesReceived = otelTesting.metrics
            .find { it.name == "smithy.client.http.bytes_received" } ?: fail("expected bytes_received")

        assertEquals(reqData.length.toLong(), actualBytesSent.longCounterSum())
        assertEquals(respData.length.toLong(), actualBytesReceived.longCounterSum())

        val bytesSentAttr = actualBytesSent.longSumData.points.first().attributes.get(AttributeKey.stringKey("server.address"))
        val bytesRecvAttr = actualBytesSent.longSumData.points.first().attributes.get(AttributeKey.stringKey("server.address"))
        val expectedAttr = "localhost:1"
        assertEquals(expectedAttr, bytesRecvAttr)
        assertEquals(expectedAttr, bytesSentAttr)
    }

    private fun MetricData.longCounterSum(): Long = longSumData.points.sumOf { it.value }
}
