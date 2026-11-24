/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.io.use
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Provider construction and wiring - the configuration mistakes that would leave a configured exporter
 * publishing nothing.
 */
class AggregatingTelemetryProviderTest {
    /**
     * Asserts on the message as well as the type: the value of this check is telling the caller which of the
     * two to remove.
     */
    @Test
    fun testExporterAndMetricReaderAreMutuallyExclusive() {
        val e = assertFailsWith<IllegalArgumentException> {
            AggregatingTelemetryProvider {
                exporter = MetricExporter.None
                metricReader = PeriodicMetricReader(1.minutes) { exporter = MetricExporter.None }
            }
        }
        assertTrue("metricReader" in e.message!! && "exporter" in e.message!!)
    }

    /** Reader-level settings on the provider builder are rejected, not silently dropped. */
    @Test
    fun testReaderSettingsRejectedWithExplicitReader() {
        assertFailsWith<IllegalArgumentException> {
            AggregatingTelemetryProvider {
                metricReader = PeriodicMetricReader(1.minutes) { exporter = MetricExporter.None }
                flushMode = FlushMode.OnDemand
            }
        }
    }

    /**
     * Configuring neither is not an error: a telemetry misconfiguration must not stop an application from
     * booting, so this degrades to recording and collecting but publishing nowhere.
     */
    @Test
    fun testNeitherExporterNorReaderStillBuilds() {
        val provider = AggregatingTelemetryProvider { }
        assertIs<AggregatingMeterProvider>(provider.meterProvider)
        provider.close()
    }

    /**
     * Guards the two-phase initialization: a provider that built its own reader, or forgot to `install`,
     * would look correct at every use site and publish nothing.
     */
    @Test
    fun testSuppliedReaderIsInstalledAndDriven() = runTest {
        val exporter = RecordingMetricExporter()
        val provider = AggregatingTelemetryProvider {
            metricReader = PeriodicMetricReader {
                this.exporter = exporter
                flushMode = FlushMode.OnDemand
            }
        }

        provider.meterProvider.getOrCreateMeter("t").createMonotonicCounter("c").add(1)
        provider.flush()

        assertEquals("c", exporter.batches.single().single().descriptor.name)
        provider.close()
    }

    /**
     * `close()` reaches the exporter through the reader. The non-suspending `close()` bridges to the reader's
     * suspending one; without that bridge the final interval would be lost silently at process exit.
     */
    @Test
    fun testCloseShutsDownTheExporter() {
        val exporter = RecordingMetricExporter()
        AggregatingTelemetryProvider { this.exporter = exporter }.close()

        assertEquals(1, exporter.shutdownCount)
    }

    /** One meter per scope, so instruments created through either reference share a series. */
    @Test
    fun testMeterIsCachedPerScope() {
        AggregatingTelemetryProvider { }.use { provider ->
            val first = provider.meterProvider.getOrCreateMeter("scope")
            assertSame(first, provider.meterProvider.getOrCreateMeter("scope"))
        }
    }

    /**
     * Tracing and context are `None` rather than half-implemented: a bespoke `TracerProvider` here would
     * silently discard spans.
     */
    @Test
    fun testTracingIsNotClaimed() {
        AggregatingTelemetryProvider { }.use { provider ->
            assertSame(TracerProvider.None, provider.tracerProvider)
            assertSame(ContextManager.None, provider.contextManager)
        }
    }
}
