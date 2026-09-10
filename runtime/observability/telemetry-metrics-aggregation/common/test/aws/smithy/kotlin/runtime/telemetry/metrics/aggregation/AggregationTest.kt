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
 * Aggregation semantics — the part of the pipeline where a mistake produces *plausible* numbers rather than
 * an error, so it needs tests more than the rest of the code does.
 *
 * These call `provider.collect()` directly instead of driving a [PeriodicMetricReader], because the
 * behaviour under test is per-cycle aggregation, not scheduling. One `collect()` call is one collection
 * cycle, which makes "what does the second interval report?" expressible as two statements rather than a
 * timing assumption.
 */
class AggregationTest {
    /**
     * `dimensions = emptySet()` by default so points collapse to a single series and assertions can use
     * `.single()`. Tests that care about dimensions opt in explicitly.
     */
    private fun provider(config: AggregationConfig = AggregationConfig(dimensions = emptySet())) = SdkMeterProvider(config, LoggerProvider.None.getOrCreateLogger("test"))

    /**
     * Sync counters are delta: each cycle reports what happened *during* that cycle.
     *
     * Guards the aggregation reset. Without it the counter would report a running total, and because a
     * rising total looks entirely reasonable on a chart, nothing else in the system would notice —
     * CloudWatch's `Sum` statistic would then double-count on every re-aggregation.
     */
    @Test
    fun testSyncCounterIsDelta() {
        val p = provider()
        val counter = p.getOrCreateMeter("test").createMonotonicCounter("calls")

        counter.add(3)
        counter.add(2)
        assertEquals(MetricValue.Sum(5.0, monotonic = true), p.collect().single().points.single().value)

        // Second interval reports only the new delta, not a running total.
        counter.add(1)
        assertEquals(MetricValue.Sum(1.0, monotonic = true), p.collect().single().points.single().value)
    }

    /**
     * An instrument with no activity is omitted entirely rather than exported as zero.
     *
     * Keeps backend cost proportional to traffic. Asserted explicitly because the natural
     * "reset the accumulator and report it" implementation would publish a zero every cycle for the life of
     * the process, which for CloudWatch is a billable metric per idle series.
     */
    @Test
    fun testIdleInstrumentIsNotCollected() {
        val p = provider()
        val counter = p.getOrCreateMeter("test").createMonotonicCounter("calls")

        counter.add(1)
        assertEquals(1, p.collect().size)
        assertTrue(p.collect().isEmpty())
    }

    /**
     * Async instruments are absolute — the mirror image of the delta test above, and the single
     * highest-risk behaviour in this module.
     *
     * A callback reports the current state of something, so consecutive readings are snapshots, not
     * increments. `depth` moving 7 -> 10 means the queue is 10 deep, not that 3 items arrived; a pipeline
     * that applied the sync counter's delta logic here would publish 3, which is a coherent and completely
     * wrong number. Asserting 10 explicitly is what stops a future refactor from unifying the two paths.
     */
    @Test
    fun testAsyncUpDownCounterIsAbsoluteNotDelta() {
        val p = provider()
        var depth = 7L
        p.getOrCreateMeter("test").createAsyncUpDownCounter("queue.depth", { it.record(depth) })

        assertEquals(MetricValue.LastValue(7.0), p.collect().single().points.single().value)

        // An absolute instrument re-reports the current value; it must NOT be diffed to 3.
        depth = 10
        assertEquals(MetricValue.LastValue(10.0), p.collect().single().points.single().value)
    }

    /**
     * `stop()` actually deregisters, so a stopped gauge stops being invoked.
     *
     * Async callbacks are held by the registry for the provider's lifetime, so a leak here is a retained
     * reference to whatever the callback closes over — and it keeps publishing a metric the caller believes
     * is gone.
     */
    @Test
    fun testGaugeHandleStopDeregisters() {
        val p = provider()
        val handle = p.getOrCreateMeter("test").createLongGauge("conns", { it.record(1) })

        assertEquals(1, p.collect().size)
        handle.stop()
        assertTrue(p.collect().isEmpty())
    }

    /**
     * One faulty callback must not take the collection cycle with it.
     *
     * Callbacks are user code running on the SDK's collection loop, so they will throw eventually. Two
     * properties matter and both are asserted: `good` is still collected in the *same* cycle (the loop does
     * not abort at the first throw), and the *next* cycle still works (the failure did not poison the loop
     * or unregister anything). The second assertion is the one that catches a well-meaning
     * "cancel the scope on error" change.
     */
    @Test
    fun testThrowingCallbackDoesNotSuppressOtherInstruments() {
        val p = provider()
        val meter = p.getOrCreateMeter("test")
        meter.createLongGauge("bad", { error("boom") })
        meter.createLongGauge("good", { it.record(42) })

        assertEquals(listOf("good"), p.collect().map { it.descriptor.name })
        // The loop survives: a later cycle still collects.
        assertEquals(listOf("good"), p.collect().map { it.descriptor.name })
    }

    /**
     * Histograms default to `Summary` and upgrade to `Distribution` only by name.
     *
     * This is the cost/fidelity default from the AWS SDK for Java v2's `detailedMetrics`, and it is a
     * default worth pinning: `Summary` is four numbers regardless of traffic, while `Distribution` grows
     * with the number of distinct values. Flipping the default would silently multiply payload size for
     * every existing user.
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
     * Only allowlisted attributes become dimensions; everything else is folded into the same series.
     *
     * The allowlist is the cost control, so the *negative* half is the important assertion: two
     * measurements differing only in a non-allowlisted attribute must produce one point, not two.
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
     * Cardinality overflow is bucketed, not dropped and not admitted.
     *
     * `maxCardinality = 2` rather than the real default of 1000 so the guard is reachable in a few lines.
     * Three series is the point of the assertion: the two that fit, plus one `overflow=true` bucket that
     * keeps the excess *visible*. Silently dropping would make a cardinality explosion look like a traffic
     * drop — the operator would investigate the wrong thing entirely.
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
     * A distribution is bounded in *distinct values*, which is a second, independent unboundedness axis —
     * the cardinality guard above does nothing here.
     *
     * A single series recording continuous values (latency in nanoseconds, say) produces an unbounded value
     * map inside one dimension set. `maxDistinctValues = 4` forces the cap immediately. The `truncated`
     * flag is asserted as well as the size: capping silently would make the summary statistics wrong with
     * no indication of why.
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
     * Instrumentation scope is preserved rather than flattened.
     *
     * Easy to get wrong and impossible to notice from the numbers: an implementation that ignores the
     * `scope` argument of `getOrCreateMeter` still satisfies the interface and still publishes plausible
     * metrics, it just permanently loses the ability to attribute them to a component.
     */
    @Test
    fun testScopeIsCarriedThroughToCollectedData() {
        val p = provider()
        p.getOrCreateMeter("Smithy.Client").createMonotonicCounter("c").add(1)

        assertEquals("Smithy.Client", p.collect().single().descriptor.scope)
    }
}
