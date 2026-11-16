/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.otel

import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetrics
import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetricsSource
import aws.smithy.kotlin.runtime.businessmetrics.SmithyBusinessMetric
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.operation.HttpDeserializer
import aws.smithy.kotlin.runtime.http.operation.HttpSerializer
import aws.smithy.kotlin.runtime.http.operation.SdkHttpOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.operation.telemetry
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.trace.SdkTracerProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val ALL_OBSERVABILITY_METRICS = setOf(
    SmithyBusinessMetric.OBSERVABILITY_TRACING,
    SmithyBusinessMetric.OBSERVABILITY_METRICS,
    SmithyBusinessMetric.OBSERVABILITY_OTEL_TRACING,
    SmithyBusinessMetric.OBSERVABILITY_OTEL_METRICS,
)

private val EXPECTED_FOR_TRACING = setOf(
    SmithyBusinessMetric.OBSERVABILITY_TRACING,
    SmithyBusinessMetric.OBSERVABILITY_OTEL_TRACING,
)

private val EXPECTED_FOR_METRICS = setOf(
    SmithyBusinessMetric.OBSERVABILITY_METRICS,
    SmithyBusinessMetric.OBSERVABILITY_OTEL_METRICS,
)

class BusinessMetricsTests {
    @Test
    fun testNoOpOpenTelemetry() = runTest {
        val provider = OpenTelemetryProvider(OpenTelemetry.noop())
        assertEquals(emptySet(), assertIs<BusinessMetricsSource>(provider.tracerProvider).businessMetrics)
        assertEquals(emptySet(), assertIs<BusinessMetricsSource>(provider.meterProvider).businessMetrics)
        assertEquals(emptySet(), observabilityMetricsFor(provider))
    }

    @Test
    fun testPropagatingOpenTelemetry() = runTest {
        val provider = OpenTelemetryProvider(OpenTelemetry.propagating(ContextPropagators.noop()))
        assertEquals(emptySet(), observabilityMetricsFor(provider))
    }

    @Test
    fun testSdkOpenTelemetry() = runTest {
        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder().build())
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(InMemoryMetricReader.create()).build())
            .build()
        sdk.use {
            assertEquals(ALL_OBSERVABILITY_METRICS, observabilityMetricsFor(OpenTelemetryProvider(it)))
        }
    }

    @Test
    fun testSdkOpenTelemetryWithOnlyTracingConfigured() = runTest {
        // The SDK fills in a meter provider for the unconfigured signal, so metrics are reported too
        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder().build())
            .build()
        sdk.use {
            assertEquals(ALL_OBSERVABILITY_METRICS, observabilityMetricsFor(OpenTelemetryProvider(it)))
        }
    }

    @Test
    fun testCustomOpenTelemetryWithOnlyTracing() = runTest {
        val provider = OpenTelemetryProvider(customOpenTelemetry(tracing = true, metrics = false))
        assertEquals(EXPECTED_FOR_TRACING, observabilityMetricsFor(provider))
    }

    @Test
    fun testCustomOpenTelemetryWithOnlyMetrics() = runTest {
        val provider = OpenTelemetryProvider(customOpenTelemetry(tracing = false, metrics = true))
        assertEquals(EXPECTED_FOR_METRICS, observabilityMetricsFor(provider))
    }
}

/**
 * A non-SDK [OpenTelemetry] implementation whose disabled signals use the API's no-op providers
 */
private fun customOpenTelemetry(tracing: Boolean, metrics: Boolean): OpenTelemetry = object : OpenTelemetry {
    private val tracerProvider = if (tracing) object : TracerProvider by TracerProvider.noop() { } else TracerProvider.noop()
    private val meterProvider = if (metrics) object : MeterProvider by MeterProvider.noop() { } else MeterProvider.noop()

    override fun getTracerProvider(): TracerProvider = tracerProvider
    override fun getMeterProvider(): MeterProvider = meterProvider
    override fun getPropagators(): ContextPropagators = ContextPropagators.noop()
}

private suspend fun observabilityMetricsFor(provider: OpenTelemetryProvider): Set<BusinessMetric> {
    val op = SdkHttpOperation.build<Unit, Unit> {
        serializeWith = object : HttpSerializer.NonStreaming<Unit> {
            override fun serialize(context: ExecutionContext, input: Unit): HttpRequestBuilder = HttpRequestBuilder()
        }
        deserializeWith = object : HttpDeserializer.NonStreaming<Unit> {
            override fun deserialize(context: ExecutionContext, call: HttpCall, payload: ByteArray?) = Unit
        }
        operationName = "TestOperation"
        serviceName = "TestService"
        telemetry { this.provider = provider }
    }
    op.roundTrip(SdkHttpClient(TestEngine()), Unit)
    return op.context.attributes.getOrNull(BusinessMetrics).orEmpty().intersect(ALL_OBSERVABILITY_METRICS)
}
