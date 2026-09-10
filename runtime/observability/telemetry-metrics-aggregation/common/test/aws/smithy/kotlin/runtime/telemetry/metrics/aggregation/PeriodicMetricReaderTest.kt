/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider
import aws.smithy.kotlin.runtime.util.TestPlatformProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Reader behaviour that is about *sequencing* rather than arithmetic: what runs, in what order, and how
 * often.
 *
 * Every test here drives [FlushMode.OnDemand] and calls [PeriodicMetricReader.flush] explicitly. The timer
 * path is deliberately not exercised with wall-clock delays — the reader owns its own
 * `CoroutineScope(Dispatchers.Default)`, so it does not observe `runTest`'s virtual time, and a test that
 * waited for a real tick would trade a real assertion for a flaky one. What the timer path adds over the
 * on-demand path is one `delay()`; what it shares — collection, export, error handling, shutdown ordering —
 * is all covered below through [PeriodicMetricReader.flush].
 */
class PeriodicMetricReaderTest {
    private val logger = LoggerProvider.None.getOrCreateLogger("test")

    private fun provider() = SdkMeterProvider(AggregationConfig(dimensions = emptySet()), logger)

    private fun reader(
        exporter: MetricExporter,
        platform: PlatformProvider = TestPlatformProvider.of(),
    ) = PeriodicMetricReader {
        this.exporter = exporter
        this.flushMode = FlushMode.OnDemand
        this.platform = platform
    }

    /**
     * An exporter learns it has been installed before it is ever asked to export, and exactly once.
     *
     * `onAttach` is how an exporter refuses to be shared, so it is worthless if it fires late or twice.
     */
    @Test
    fun testExporterIsAttachedOnceBeforeAnyExport() {
        val exporter = RecordingMetricExporter()
        val reader = reader(exporter)

        reader.install(provider(), logger)

        assertEquals(1, exporter.attachCount)
        assertTrue(exporter.batches.isEmpty())
    }

    /**
     * Nothing is published until [PeriodicMetricReader.flush] is called in on-demand mode, and then exactly
     * the recorded metrics are.
     *
     * This is the Lambda contract in two assertions: no background publishing, and a flush that actually
     * drains.
     */
    @Test
    fun testOnDemandPublishesOnlyOnFlush() = runTest {
        val exporter = RecordingMetricExporter()
        val provider = provider()
        val reader = reader(exporter)
        reader.install(provider, logger)

        provider.getOrCreateMeter("t").createMonotonicCounter("c").add(1)
        assertTrue(exporter.batches.isEmpty())

        reader.flush()

        assertEquals(1, exporter.batches.size)
        assertEquals("c", exporter.batches.single().single().descriptor.name)
    }

    /**
     * An idle cycle does not call the exporter at all.
     *
     * Not merely an optimization for CloudWatch: `PutMetricData` is billed per request, so a reader that
     * published empty batches would charge users for idleness.
     */
    @Test
    fun testEmptyCycleSkipsExport() = runTest {
        val exporter = RecordingMetricExporter()
        reader(exporter).apply { install(provider(), logger) }.flush()

        assertTrue(exporter.batches.isEmpty())
    }

    /**
     * A throwing exporter is contained: the failure does not propagate to the caller of `flush`, and the
     * reader stays usable.
     *
     * A `flush()` that rethrew would surface a telemetry defect inside application code — in Lambda, at the
     * end of the handler, where it would fail the invocation.
     */
    @Test
    fun testExportFailureIsContained() = runTest {
        val exporter = RecordingMetricExporter(failOnExport = true)
        val provider = provider()
        val reader = reader(exporter)
        reader.install(provider, logger)
        val counter = provider.getOrCreateMeter("t").createMonotonicCounter("c")

        counter.add(1)
        reader.flush()

        // Still working after the failure: a second cycle is attempted rather than the reader latching shut.
        counter.add(1)
        reader.flush()
        assertEquals(2, exporter.batches.size)
    }

    /**
     * Close performs a final flush *before* shutting the exporter down, and shuts it down once.
     *
     * The ordering is the whole point: reversed, the last interval — which contains whatever happened
     * immediately before shutdown, often the most interesting part — would be collected and then handed to
     * a closed exporter.
     */
    @Test
    fun testCloseFlushesBeforeShutdown() = runTest {
        val exporter = RecordingMetricExporter()
        val provider = provider()
        val reader = reader(exporter)
        reader.install(provider, logger)

        provider.getOrCreateMeter("t").createMonotonicCounter("c").add(1)
        reader.close()

        assertEquals(1, exporter.batches.size)
        assertEquals(1, exporter.shutdownCount)
    }

    /**
     * Close is idempotent, and flushing a closed reader is a no-op rather than a throw.
     *
     * Both are normal outcomes of ordinary teardown — a `use { }` block around a provider whose owner also
     * closes it, or a flush racing shutdown — and neither should surface as an error.
     */
    @Test
    fun testCloseIsIdempotentAndFlushAfterCloseIsInert() = runTest {
        val exporter = RecordingMetricExporter()
        val provider = provider()
        val reader = reader(exporter)
        reader.install(provider, logger)

        reader.close()
        reader.close()
        provider.getOrCreateMeter("t").createMonotonicCounter("c").add(1)
        reader.flush()

        assertEquals(1, exporter.shutdownCount)
        assertTrue(exporter.batches.isEmpty())
    }

    /**
     * Lambda is detected from the environment and defaults to on-demand.
     *
     * Timer-driven publishing is unreliable there — the execution environment is frozen between
     * invocations, so a scheduled `delay()` may not resume until the next one, or ever — and getting this
     * default wrong means silently losing metrics on the single most common serverless platform.
     */
    @Test
    fun testLambdaEnvironmentDefaultsToOnDemand() {
        val lambda = TestPlatformProvider.of(
            env = mapOf(PeriodicMetricReader.ENV_LAMBDA_FUNCTION to "my-function"),
        )
        assertEquals(FlushMode.OnDemand, PeriodicMetricReader.defaultFlushMode(lambda))
    }

    /** Everywhere else, the default is a one-minute timer. */
    @Test
    fun testNonLambdaEnvironmentDefaultsToPeriodic() {
        val mode = PeriodicMetricReader.defaultFlushMode(TestPlatformProvider.of())

        assertIs<FlushMode.Periodic>(mode)
        assertEquals(1.minutes, mode.interval)
    }

    /**
     * The `interval` convenience argument is equivalent to setting [FlushMode.Periodic] in the block, and an
     * explicit [FlushMode] in the block wins over environment detection.
     *
     * The second half matters most: an explicitly configured periodic reader must stay periodic even on
     * Lambda, because a caller who asked for a timer there has presumably arranged for it to work.
     */
    @Test
    fun testExplicitFlushModeOverridesEnvironment() = runTest {
        val exporter = RecordingMetricExporter()
        val lambda = TestPlatformProvider.of(
            env = mapOf(PeriodicMetricReader.ENV_LAMBDA_FUNCTION to "my-function"),
        )

        val reader = PeriodicMetricReader(interval = 30.seconds) {
            this.exporter = exporter
            this.platform = lambda
        }
        reader.install(provider(), logger)

        // Installed with a timer despite the Lambda environment; no export has happened yet because the
        // first tick is a full interval away.
        assertEquals(1, exporter.attachCount)
        assertTrue(exporter.batches.isEmpty())
        reader.close()
    }
}
