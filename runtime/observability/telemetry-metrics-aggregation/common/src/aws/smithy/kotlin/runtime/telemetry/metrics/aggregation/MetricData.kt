/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.time.Instant

/*
 * The public types here are hand-written value classes rather than `data class`es. A `data class` cannot
 * gain a constructor parameter without breaking binary compatibility, and this model has to absorb new
 * aggregations and new descriptor fields as backends need them. `equals`/`hashCode` are written out
 * because several of these are used as map keys; `Double` fields compare with `equals` rather than `==`
 * so that `NaN` equals itself and the contract holds for keys and set members.
 */

/**
 * Identifies the instrument that produced a [MetricData].
 *
 * Doubles as the registry key for synchronous instruments: repeated `createMonotonicCounter("calls")`
 * calls must resolve to the same aggregation state rather than each publishing a partial series.
 *
 * @param scope the instrumentation scope from `MeterProvider.getOrCreateMeter`; the SDK passes the
 *   service client's name.
 * @param name used verbatim as the metric name by exporters.
 * @param units an optional unit string (`"ms"`, `"By"`, ...) mapped to a backend-specific unit at export
 *   time. Null means unitless.
 * @param description optional text, ignored by backends with no field for it.
 */
public class InstrumentDescriptor(
    public val scope: String,
    public val name: String,
    public val units: String? = null,
    public val description: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is InstrumentDescriptor &&
        other.scope == scope &&
        other.name == name &&
        other.units == units &&
        other.description == description

    override fun hashCode(): Int {
        var result = scope.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (units?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "InstrumentDescriptor(scope=$scope, name=$name, units=$units, description=$description)"
}

/**
 * The aggregated value of one instrument for one attribute set over one collection interval.
 *
 * | Variant        | Memory per attribute set   | Percentiles | Temporality |
 * |----------------|----------------------------|-------------|-------------|
 * | [Sum]          | 1 number                   | no          | delta       |
 * | [LastValue]    | 1 number                   | no          | absolute    |
 * | [Summary]      | 4 numbers (fixed)          | no          | delta       |
 * | [Distribution] | grows with distinct values | yes         | delta       |
 */
public sealed interface MetricValue {
    /**
     * A **delta** sum over the collection interval, not a running total, because backends such as
     * CloudWatch already aggregate within each period - a cumulative counter at 100 would publish 100
     * every minute and sum to 6000 over an idle hour. This differs from OpenTelemetry's default
     * temporality.
     *
     * @param value the delta. May be negative when [monotonic] is false.
     * @param monotonic true for `MonotonicCounter`, false for `UpDownCounter`, for exporters that
     *   distinguish the two.
     */
    public class Sum(public val value: Double, public val monotonic: Boolean) : MetricValue {
        override fun equals(other: Any?): Boolean = other is Sum && other.value.equals(value) && other.monotonic == monotonic
        override fun hashCode(): Int = 31 * value.hashCode() + monotonic.hashCode()
        override fun toString(): String = "Sum(value=$value, monotonic=$monotonic)"
    }

    /**
     * An **absolute** value sampled at collection time, produced only by async instruments.
     *
     * Never diff this against the previous interval: a gauge reporting queue depth 7 then 10 means the
     * queue is now 10, not that 3 items were added.
     */
    public class LastValue(public val value: Double) : MetricValue {
        override fun equals(other: Any?): Boolean = other is LastValue && other.value.equals(value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "LastValue(value=$value)"
    }

    /**
     * Count / sum / min / max only - maps to a CloudWatch
     * [StatisticSet](https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_StatisticSet.html).
     *
     * The default aggregation for histograms: four numbers per attribute set however many values are
     * recorded, which is what makes it safe to enable everywhere. A backend cannot derive percentiles
     * from it, so p99 latency requires [Distribution].
     */
    public class Summary(
        public val count: Long,
        public val sum: Double,
        public val min: Double,
        public val max: Double,
    ) : MetricValue {
        override fun equals(other: Any?): Boolean = other is Summary &&
            other.count == count &&
            other.sum.equals(sum) &&
            other.min.equals(min) &&
            other.max.equals(max)

        override fun hashCode(): Int {
            var result = count.hashCode()
            result = 31 * result + sum.hashCode()
            result = 31 * result + min.hashCode()
            result = 31 * result + max.hashCode()
            return result
        }

        override fun toString(): String = "Summary(count=$count, sum=$sum, min=$min, max=$max)"
    }

    /**
     * Distinct values with their occurrence counts - maps to CloudWatch
     * [`Values`](https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_MetricDatum.html#ACW-Type-MetricDatum-Values)
     * and [`Counts`](https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_MetricDatum.html#ACW-Type-MetricDatum-Counts).
     *
     * Required for percentiles, and opt-in per instrument because memory grows with the number of
     * *distinct* values and a latency histogram's values are near-unique.
     *
     * @param values value to occurrence count. Chunked at export to the backend's pair limit.
     * @param truncated true when the distinct-value bound was reached and further new values were
     *   discarded, so an exporter can warn rather than let partial data read as complete.
     */
    public class Distribution(
        public val values: Map<Double, Long>,
        public val truncated: Boolean = false,
    ) : MetricValue {
        override fun equals(other: Any?): Boolean = other is Distribution && other.values == values && other.truncated == truncated
        override fun hashCode(): Int = 31 * values.hashCode() + truncated.hashCode()

        /** Summarises rather than dumps: a latency distribution holds one entry per distinct value. */
        override fun toString(): String = "Distribution(distinctValues=${values.size}, truncated=$truncated)"
    }
}

/**
 * A dimension name/value pair. Lists handed to an exporter are sorted by name, so attribute sets that
 * differ only in population order produce equal keys instead of splitting the series.
 */
public class Dimension(public val name: String, public val value: String) {
    override fun equals(other: Any?): Boolean = other is Dimension && other.name == name && other.value == value
    override fun hashCode(): Int = 31 * name.hashCode() + value.hashCode()
    override fun toString(): String = "$name=$value"
}

/**
 * A single time series: one dimension set plus its aggregated value. A counter split by operation name
 * yields one point per operation, each a separately billed backend metric.
 */
public class MetricPoint(
    public val dimensions: List<Dimension>,
    public val value: MetricValue,
) {
    override fun equals(other: Any?): Boolean = other is MetricPoint && other.dimensions == dimensions && other.value == value
    override fun hashCode(): Int = 31 * dimensions.hashCode() + value.hashCode()
    override fun toString(): String = "MetricPoint(dimensions=$dimensions, value=$value)"
}

/**
 * Everything collected for one instrument in one interval - the unit handed to [MetricExporter].
 *
 * @param descriptor identifies the instrument these points came from.
 * @param timestamp stamped once per collection cycle, so every point in a cycle lines up on a chart.
 * @param points one entry per attribute set that recorded at least one measurement this cycle.
 */
public class MetricData(
    public val descriptor: InstrumentDescriptor,
    public val timestamp: Instant,
    public val points: List<MetricPoint>,
) {
    override fun equals(other: Any?): Boolean = other is MetricData &&
        other.descriptor == descriptor &&
        other.timestamp == timestamp &&
        other.points == points

    override fun hashCode(): Int {
        var result = descriptor.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + points.hashCode()
        return result
    }

    override fun toString(): String = "MetricData(descriptor=$descriptor, timestamp=$timestamp, points=$points)"
}
