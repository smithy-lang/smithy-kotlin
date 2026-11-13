/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetrics
import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetricsSource
import aws.smithy.kotlin.runtime.businessmetrics.SmithyBusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.containsBusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.withScopedBusinessMetrics
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.util.RecordingTelemetryProvider
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.telemetry.AbstractTelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.AbstractMeterProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.AbstractTracerProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdkHttpOperationBusinessMetricsTest {
    private val client = SdkHttpClient(TestEngine())

    private fun newOp() = newTestOperation<Unit, Unit>(HttpRequestBuilder(), Unit)

    // Only the metrics under test; others (e.g. retry mode) are emitted by every operation
    private val testedMetrics = setOf(
        SmithyBusinessMetric.WAITER,
        SmithyBusinessMetric.PAGINATOR,
        SmithyBusinessMetric.OBSERVABILITY_TRACING,
        SmithyBusinessMetric.OBSERVABILITY_METRICS,
        SmithyBusinessMetric.OBSERVABILITY_OTEL_TRACING,
        SmithyBusinessMetric.OBSERVABILITY_OTEL_METRICS,
    )

    private val SdkHttpOperation<*, *>.businessMetrics: Set<BusinessMetric>
        get() = context.attributes.getOrNull(BusinessMetrics).orEmpty().intersect(testedMetrics)

    @Test
    fun testNoBusinessMetricsByDefault() = runTest {
        val op = newOp()
        op.roundTrip(client, Unit)
        assertEquals(emptySet(), op.businessMetrics)
    }

    @Test
    fun testScopedBusinessMetricsEmitted() = runTest {
        val op = newOp()
        withScopedBusinessMetrics(SmithyBusinessMetric.WAITER) {
            withScopedBusinessMetrics(SmithyBusinessMetric.PAGINATOR) {
                op.roundTrip(client, Unit)
            }
        }
        assertEquals(setOf(SmithyBusinessMetric.WAITER, SmithyBusinessMetric.PAGINATOR), op.businessMetrics)
    }

    @Test
    fun testScopedBusinessMetricsEmittedForEachOperation() = runTest {
        val ops = List(3) { newOp() }
        withScopedBusinessMetrics(SmithyBusinessMetric.PAGINATOR) {
            ops.forEach { it.roundTrip(client, Unit) }
        }
        ops.forEach { assertTrue(it.context.containsBusinessMetric(SmithyBusinessMetric.PAGINATOR)) }
    }

    @Test
    fun testScopedBusinessMetricsNotInheritedByNestedOperations() = runTest {
        val outer = newOp()
        val nested = newOp()
        withScopedBusinessMetrics(SmithyBusinessMetric.WAITER) {
            outer.execute(client, Unit) {
                nested.roundTrip(client, Unit)
            }
        }
        assertTrue(outer.context.containsBusinessMetric(SmithyBusinessMetric.WAITER))
        assertFalse(nested.context.containsBusinessMetric(SmithyBusinessMetric.WAITER))
    }

    @Test
    fun testTracingProvider() = runTest {
        val op = newOp()
        op.telemetry.provider = fakeTelemetryProvider(tracerProvider = object : AbstractTracerProvider() { })
        op.roundTrip(client, Unit)
        assertEquals(setOf(SmithyBusinessMetric.OBSERVABILITY_TRACING), op.businessMetrics)
    }

    @Test
    fun testMetricsProvider() = runTest {
        val op = newOp()
        op.telemetry.provider = RecordingTelemetryProvider()
        op.roundTrip(client, Unit)
        assertEquals(setOf(SmithyBusinessMetric.OBSERVABILITY_METRICS), op.businessMetrics)
    }

    @Test
    fun testBusinessMetricsSourceProviders() = runTest {
        val op = newOp()
        op.telemetry.provider = fakeTelemetryProvider(
            tracerProvider = object : AbstractTracerProvider(), BusinessMetricsSource {
                override val businessMetrics = setOf(SmithyBusinessMetric.OBSERVABILITY_OTEL_TRACING)
            },
            meterProvider = object : AbstractMeterProvider(), BusinessMetricsSource {
                override val businessMetrics = setOf(SmithyBusinessMetric.OBSERVABILITY_OTEL_METRICS)
            },
        )
        op.roundTrip(client, Unit)

        val expected = setOf(SmithyBusinessMetric.OBSERVABILITY_OTEL_TRACING, SmithyBusinessMetric.OBSERVABILITY_OTEL_METRICS)
        assertEquals(expected, op.businessMetrics)
    }

    @Test
    fun testEmptyBusinessMetricsSourceProviders() = runTest {
        val op = newOp()
        op.telemetry.provider = fakeTelemetryProvider(
            tracerProvider = object : AbstractTracerProvider(), BusinessMetricsSource {
                override val businessMetrics = emptySet<BusinessMetric>()
            },
            meterProvider = object : AbstractMeterProvider(), BusinessMetricsSource {
                override val businessMetrics = emptySet<BusinessMetric>()
            },
        )
        op.roundTrip(client, Unit)
        assertEquals(emptySet(), op.businessMetrics)
    }
}

private fun fakeTelemetryProvider(
    tracerProvider: TracerProvider = TracerProvider.None,
    meterProvider: MeterProvider = MeterProvider.None,
): TelemetryProvider = object : AbstractTelemetryProvider() {
    override val tracerProvider: TracerProvider = tracerProvider
    override val meterProvider: MeterProvider = meterProvider
}
