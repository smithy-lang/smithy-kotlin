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
 * Reader sequencing: what runs, in what order, how often.
 *
 * Every test drives [FlushMode.OnDemand] and flushes explicitly. The reader owns a
 * `CoroutineScope(Dispatchers.Default)` and so does not observe `runTest`'s virtual time, and waiting on a
 * real tick would trade an assertion for a flake. All the timer path adds over the on-demand path is one
 * `delay()`.
 */
class PeriodicMetricReaderTest {
    private val logger = LoggerProvider.None.getOrCreateLogger("test")

    private fun provider() = AggregatingMeterProvider(AggregationConfig(dimensions = emptySet()), logger)

    private fun reader(
        exporter: MetricExporter,
        platform: PlatformProvider = TestPlatformProvider.of(),
    ) = PeriodicMetricReader {
        this.exporter = exporter
        this.flushMode = FlushMode.OnDemand
        this.platform = platform
    }

    /** The Lambda contract in two assertions: no background publishing, and a flush that actually drains. */
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

    /** An idle cycle does not call the exporter: `PutMetricData` is billed per request. */
    @Test
    fun testEmptyCycleSkipsExport() = runTest {
        val exporter = RecordingMetricExporter()
        reader(exporter).apply { install(provider(), logger) }.flush()

        assertTrue(exporter.batches.isEmpty())
    }

    /**
     * A throwing exporter does not propagate to the caller of `flush` - in Lambda that would fail the
     * invocation - and the reader stays usable rather than latching shut.
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

        counter.add(1)
        reader.flush()
        assertEquals(2, exporter.batches.size)
    }

    /**
     * Close flushes before shutting the exporter down. Reversed, the last interval - often the most
     * interesting part - would be collected and handed to a closed exporter.
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
     * Double close and flush-after-close are both inert. Both arise from ordinary teardown - nested `use { }`
     * blocks, a flush racing shutdown - and neither should surface as an error.
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
     * Lambda defaults to on-demand. A timer is unreliable there - the environment is frozen between
     * invocations, so a scheduled `delay()` may not resume until the next one, or ever.
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
     * An explicit [FlushMode] wins over environment detection: a caller who asked for a timer on Lambda has
     * presumably arranged for it to work.
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

        // Nothing exported yet: the first tick is a full interval away.
        assertTrue(exporter.batches.isEmpty())
        reader.close()
    }
}
