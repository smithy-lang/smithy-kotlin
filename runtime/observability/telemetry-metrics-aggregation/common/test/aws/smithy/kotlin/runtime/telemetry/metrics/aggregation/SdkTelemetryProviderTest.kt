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
 * Provider construction and wiring — the configuration mistakes whose absence would leave a configured
 * exporter publishing nothing.
 */
class SdkTelemetryProviderTest {
    /**
     * The ambiguous combination must fail loudly. Asserting on the *message* as well as the type, because
     * the whole value of this check is that it tells the caller which of the two to remove — an
     * `IllegalArgumentException` with no guidance would be little better than the silent behaviour it
     * replaces.
     */
    @Test
    fun testExporterAndMetricReaderAreMutuallyExclusive() {
        val e = assertFailsWith<IllegalArgumentException> {
            SdkTelemetryProvider {
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
            SdkTelemetryProvider {
                metricReader = PeriodicMetricReader(1.minutes) { exporter = MetricExporter.None }
                flushMode = FlushMode.OnDemand
            }
        }
    }

    /**
     * The opposite direction, and just as important: configuring *neither* is not an error. A telemetry
     * misconfiguration must not stop an application from booting, so this degrades to a provider that
     * records and collects but publishes nowhere.
     */
    @Test
    fun testNeitherExporterNorReaderStillBuilds() {
        val provider = SdkTelemetryProvider { }
        assertIs<SdkMeterProvider>(provider.meterProvider)
        provider.close()
    }

    /**
     * The reader supplied by the caller is the one installed and driven.
     *
     * Guards the two-phase initialization: a provider that constructed its own reader instead — or that
     * forgot to call `install` — would look correct at every use site and publish nothing.
     */
    @Test
    fun testSuppliedReaderIsInstalledAndDriven() = runTest {
        val exporter = RecordingMetricExporter()
        val provider = SdkTelemetryProvider {
            metricReader = PeriodicMetricReader {
                this.exporter = exporter
                flushMode = FlushMode.OnDemand
            }
        }

        assertEquals(1, exporter.attachCount)

        provider.meterProvider.getOrCreateMeter("t").createMonotonicCounter("c").add(1)
        provider.flush()

        assertEquals("c", exporter.batches.single().single().descriptor.name)
        provider.close()
    }

    /**
     * `close()` reaches the exporter through the reader, so `use { }` on the provider is a complete
     * shutdown.
     *
     * The non-suspending `close()` bridges to the reader's suspending one; if that bridge were dropped, the
     * final interval would be lost silently at process exit.
     */
    @Test
    fun testCloseShutsDownTheExporter() {
        val exporter = RecordingMetricExporter()
        SdkTelemetryProvider { this.exporter = exporter }.close()

        assertEquals(1, exporter.shutdownCount)
    }

    /**
     * Repeated `getOrCreateMeter` calls for one scope return the same meter, so instruments created through
     * either reference accumulate into the same series.
     */
    @Test
    fun testMeterIsCachedPerScope() {
        SdkTelemetryProvider { }.use { provider ->
            val first = provider.meterProvider.getOrCreateMeter("scope")
            assertSame(first, provider.meterProvider.getOrCreateMeter("scope"))
        }
    }

    /**
     * Tracing and context are explicitly absent rather than half-implemented.
     *
     * A metrics pipeline that returned a bespoke `TracerProvider` would silently discard spans — exactly the
     * failure mode this module exists to eliminate for gauges. `None` is honest: callers who need tracing
     * see it is not offered.
     */
    @Test
    fun testTracingIsNotClaimed() {
        SdkTelemetryProvider { }.use { provider ->
            assertSame(TracerProvider.None, provider.tracerProvider)
            assertSame(ContextManager.None, provider.contextManager)
        }
    }
}
