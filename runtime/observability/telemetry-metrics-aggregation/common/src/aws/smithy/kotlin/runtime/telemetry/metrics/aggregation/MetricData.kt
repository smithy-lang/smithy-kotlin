/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.time.Instant

/**
 * Identifies the instrument that produced a [MetricData].
 *
 * This is the pipeline's export-facing view of an instrument. It is deliberately a `data class`:
 * it doubles as the registry key for synchronous instruments, so structural equality is required.
 * Two `createMonotonicCounter("calls")` calls on the same `Meter` must resolve to the *same*
 * aggregation state, otherwise each call site would publish its own partial series.
 *
 * @param scope the instrumentation scope from `MeterProvider.getOrCreateMeter`. Retained rather
 *   than discarded so exporters can attribute a metric to the library that emitted it — the SDK
 *   passes the service client's name here.
 * @param name the instrument name, used verbatim as the metric name by exporters.
 * @param units an optional unit string (`"ms"`, `"By"`, …) mapped to a backend-specific unit at
 *   export time. Null means "unitless"; it is not an error.
 * @param description optional human-readable text. Carried for exporters that can use it; backends
 *   with no field for it ignore it.
 */
public data class InstrumentDescriptor(
    public val scope: String,
    public val name: String,
    public val units: String? = null,
    public val description: String? = null,
)

/**
 * The aggregated value of one instrument for one attribute set over one collection interval.
 *
 * The four variants exist because metric backends accept different datum shapes, and because they
 * trade memory against fidelity differently:
 *
 * | Variant          | Memory per attribute set   | Percentiles | Temporality |
 * |------------------|----------------------------|-------------|-------------|
 * | [Sum]            | 1 number                   | no          | delta       |
 * | [LastValue]      | 1 number                   | no          | absolute    |
 * | [Summary]        | 4 numbers (fixed)          | no          | delta       |
 * | [Distribution]   | grows with distinct values | yes         | delta       |
 *
 * The temporality column is the subtle one and the source of the easiest bug in this layer: see
 * [LastValue].
 */
public sealed interface MetricValue {
    /**
     * A **delta** sum over the collection interval — the amount added *since the last collect*,
     * not a running total.
     *
     * Delta rather than cumulative because backends such as CloudWatch already aggregate within
     * each period. If this carried a cumulative total, a counter at 100 would publish 100 every
     * minute and a `Sum` statistic would report 6000 for an hour in which nothing happened. Note
     * this differs from OpenTelemetry's default temporality, which is cumulative.
     *
     * @param value the delta. May be negative when [monotonic] is false.
     * @param monotonic true for `MonotonicCounter`, false for `UpDownCounter`. Retained so an
     *   exporter that distinguishes the two (Prometheus separates counters from gauges) can, even
     *   though exporters that treat them identically are free to ignore it.
     */
    public data class Sum(public val value: Double, public val monotonic: Boolean) : MetricValue

    /**
     * An **absolute** value sampled at collection time — produced only by async instruments
     * (gauges and async up/down counters).
     *
     * This must never be diffed against the previous interval. A gauge reporting queue depth 7
     * then 10 means "the queue is now 10", not "3 items were added". Converting this to a [Sum]
     * is the highest-value bug to guard against in this module because the result is plausible
     * rather than obviously broken — hence the dedicated test asserting that consecutive readings
     * are published as-is.
     */
    public data class LastValue(public val value: Double) : MetricValue

    /**
     * Count / sum / min / max only — maps to a CloudWatch
     * [StatisticSet](https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_StatisticSet.html).
     *
     * The default aggregation for histograms. Its decisive property is a **fixed** footprint of
     * four numbers per attribute set no matter how many values are recorded, which is what makes
     * it safe to enable everywhere by default.
     *
     * The cost of that bound is that a backend cannot compute percentiles from it (except in
     * degenerate single-sample cases), so p99 latency requires [Distribution].
     *
     * The CloudWatch mapping is named here only to explain *why* these four fields and no others.
     * This module has no CloudWatch dependency, and this shape is exporter-agnostic — a Prometheus
     * or OTLP exporter would consume it just as well.
     */
    public data class Summary(
        public val count: Long,
        public val sum: Double,
        public val min: Double,
        public val max: Double,
    ) : MetricValue

    /**
     * Distinct values with their occurrence counts — maps to CloudWatch
     * [`Values`](https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_MetricDatum.html#ACW-Type-MetricDatum-Values)
     * and [`Counts`](https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_MetricDatum.html#ACW-Type-MetricDatum-Counts).
     *
     * Required for percentiles, and opt-in per instrument for exactly that reason: memory grows
     * with the number of *distinct* values, and a latency histogram's values are near-unique.
     *
     * @param values value to occurrence count. Chunked at export to whatever pair limit the backend
     *   imposes.
     * @param truncated true when the per-instrument distinct-value bound was reached and further
     *   new values were discarded. Surfaced rather than hidden so the exporter can warn: silently
     *   truncated data would read as complete and mislead whoever is reading the chart.
     */
    public data class Distribution(
        public val values: Map<Double, Long>,
        public val truncated: Boolean = false,
    ) : MetricValue
}

/**
 * A dimension name/value pair.
 *
 * Every list handed to an exporter is sorted by name, so that two logically identical attribute sets
 * always produce equal keys regardless of the order the SDK happened to populate the attributes in.
 * Without that normalization the same series would split in two.
 */
public data class Dimension(public val name: String, public val value: String)

/**
 * A single time series: one dimension set plus its aggregated value.
 *
 * One [MetricData] holds many of these — a counter split by operation name yields one point per
 * operation, and each becomes a separately billed backend metric.
 */
public data class MetricPoint(
    public val dimensions: List<Dimension>,
    public val value: MetricValue,
)

/**
 * Everything collected for one instrument in one interval — the unit handed to [MetricExporter].
 *
 * @param descriptor identifies the instrument these points came from.
 * @param timestamp stamped once per collection cycle rather than per measurement, so every point
 *   in a cycle shares a timestamp. This is what lets a backend line the series up on a chart;
 *   per-measurement timestamps would scatter points across a period for no benefit. Note that
 *   CloudWatch rejects timestamps more than two weeks old or two hours in the future, so a
 *   long-stalled export can be rejected wholesale — a reason the reader flushes on close.
 * @param points one entry per attribute set that recorded at least one measurement this cycle.
 */
public data class MetricData(
    public val descriptor: InstrumentDescriptor,
    public val timestamp: Instant,
    public val points: List<MetricPoint>,
)
