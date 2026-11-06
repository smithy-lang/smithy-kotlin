/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Per-cycle aggregation semantics, where a mistake produces plausible numbers rather than an error.
 *
 * These call `provider.collect()` directly rather than driving a [PeriodicMetricReader]: one call is one
 * collection cycle, which makes "what does the second interval report?" an assertion rather than a timing
 * assumption.
 */
class AggregationTest {
    /** Defaults to no dimensions so points collapse to a single series and assertions can use `.single()`. */
    private fun provider(config: AggregationConfig = AggregationConfig(dimensions = emptySet())) = AggregatingMeterProvider(config, LoggerProvider.None.getOrCreateLogger("test"))

    /** Sync counters are delta: each cycle reports only what happened during it, never a running total. */
    @Test
    fun testSyncCounterIsDelta() {
        val p = provider()
        val counter = p.getOrCreateMeter("test").createMonotonicCounter("calls")

        counter.add(3)
        counter.add(2)
        assertEquals(MetricValue.Sum(5.0, monotonic = true), p.collect().single().points.single().value)

        counter.add(1)
        assertEquals(MetricValue.Sum(1.0, monotonic = true), p.collect().single().points.single().value)
    }

    /** An idle instrument is omitted rather than exported as zero, which for CloudWatch would be billable. */
    @Test
    fun testIdleInstrumentIsNotCollected() {
        val p = provider()
        val counter = p.getOrCreateMeter("test").createMonotonicCounter("calls")

        counter.add(1)
        assertEquals(1, p.collect().size)
        assertTrue(p.collect().isEmpty())
    }

    /**
     * Async instruments are absolute - the mirror image of [testSyncCounterIsDelta].
     *
     * `depth` moving 7 -> 10 means the queue is 10 deep, not that 3 items arrived. Applying the sync
     * counter's delta logic here would publish 3, a coherent and completely wrong number.
     */
    @Test
    fun testAsyncUpDownCounterIsAbsoluteNotDelta() {
        val p = provider()
        var depth = 7L
        p.getOrCreateMeter("test").createAsyncUpDownCounter("queue.depth", { it.record(depth) })

        assertEquals(MetricValue.LastValue(7.0), p.collect().single().points.single().value)

        depth = 10
        assertEquals(MetricValue.LastValue(10.0), p.collect().single().points.single().value)
    }

    /** `stop()` deregisters, so a stopped gauge is neither invoked nor retained. */
    @Test
    fun testGaugeHandleStopDeregisters() {
        val p = provider()
        val handle = p.getOrCreateMeter("test").createLongGauge("conns", { it.record(1) })

        assertEquals(1, p.collect().size)
        handle.stop()
        assertTrue(p.collect().isEmpty())
    }

    /**
     * A throwing callback takes neither the rest of the cycle nor the loop with it. The second assertion is
     * the one that catches a well-meaning "cancel the scope on error" change.
     */
    @Test
    fun testThrowingCallbackDoesNotSuppressOtherInstruments() {
        val p = provider()
        val meter = p.getOrCreateMeter("test")
        meter.createLongGauge("bad", { error("boom") })
        meter.createLongGauge("good", { it.record(42) })

        assertEquals(listOf("good"), p.collect().map { it.descriptor.name })
        assertEquals(listOf("good"), p.collect().map { it.descriptor.name })
    }

    /**
     * Histograms default to `Summary` and upgrade to `Distribution` only by name. Worth pinning: `Summary` is
     * four numbers regardless of traffic, so flipping the default would multiply payload size for everyone.
     */
    @Test
    fun testHistogramDefaultsToSummaryAndOptsInToDistribution() {
        val summaryOnly = provider()
        summaryOnly.getOrCreateMeter("t").createDoubleHistogram("latency").record(1.5)
        assertTrue(summaryOnly.collect().single().points.single().value is MetricValue.Summary)

        val detailed = provider(
            AggregationConfig(dimensions = emptySet(), detailedMetrics = setOf("latency")),
        )
        detailed.getOrCreateMeter("t").createDoubleHistogram("latency").record(1.5)
        assertTrue(detailed.collect().single().points.single().value is MetricValue.Distribution)
    }

    /**
     * Only allowlisted attributes become dimensions. The negative half is the point: two measurements
     * differing only in a non-allowlisted attribute must produce one point, not two.
     */
    @Test
    fun testOnlyAllowlistedAttributesBecomeDimensions() {
        val p = provider(AggregationConfig(dimensions = setOf("op")))
        val counter = p.getOrCreateMeter("t").createMonotonicCounter("c")

        counter.add(
            1,
            attributesOf {
                "op" to "GetObject"
                "requestId" to "a"
            },
        )
        counter.add(
            1,
            attributesOf {
                "op" to "GetObject"
                "requestId" to "b"
            },
        )

        val point = p.collect().single().points.single()
        assertEquals(listOf(Dimension("op", "GetObject")), point.dimensions)
        assertEquals(MetricValue.Sum(2.0, monotonic = true), point.value)
    }

    /**
     * Cardinality overflow is bucketed, not dropped: dropping would make an explosion look like a traffic
     * drop. `maxCardinality = 2` instead of the real default so the guard is reachable in a few lines.
     */
    @Test
    fun testCardinalityGuardBucketsOverflow() {
        val p = provider(AggregationConfig(dimensions = setOf("k"), maxCardinality = 2))
        val counter = p.getOrCreateMeter("t").createMonotonicCounter("c")

        repeat(10) { i -> counter.add(1, attributesOf { "k" to "v$i" }) }

        val dims = p.collect().single().points.map { it.dimensions }
        assertEquals(3, dims.size) // 2 admitted + 1 overflow bucket
        assertTrue(dims.any { it == listOf(Dimension("overflow", "true")) })
    }

    /**
     * Distinct values within one series are bounded too - a second axis the cardinality guard does nothing
     * about. `truncated` is asserted alongside the size: capping silently would make the statistics wrong
     * with no indication of why.
     */
    @Test
    fun testDistributionIsBounded() {
        val p = provider(
            AggregationConfig(
                dimensions = emptySet(),
                detailedMetrics = setOf("h"),
                maxDistinctValues = 4,
            ),
        )
        val h = p.getOrCreateMeter("t").createDoubleHistogram("h")
        repeat(100) { h.record(it.toDouble()) }

        val dist = p.collect().single().points.single().value as MetricValue.Distribution
        assertEquals(4, dist.values.size)
        assertTrue(dist.truncated)
    }

    /**
     * Instrumentation scope survives to the collected data. An implementation that ignored `scope` would
     * still satisfy the interface and still publish plausible metrics, just unattributable ones.
     */
    @Test
    fun testScopeIsCarriedThroughToCollectedData() {
        val p = provider()
        p.getOrCreateMeter("Smithy.Client").createMonotonicCounter("c").add(1)

        assertEquals("Smithy.Client", p.collect().single().descriptor.scope)
    }
}
