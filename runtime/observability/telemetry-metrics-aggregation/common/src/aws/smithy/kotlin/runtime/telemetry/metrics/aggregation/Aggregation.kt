/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * Accumulates measurements for one (instrument, attribute set) pair.
 *
 * ### Locking
 *
 * Every implementation takes a `reentrantLock` rather than using atomics. Atomics would be faster
 * for [SumAggregator] alone, but [SummaryAggregator] must update four fields as one unit — a torn
 * read yielding `min > max`, or a `count` that disagrees with `sum`, would publish an arithmetically
 * impossible statistic set that backends reject. Uniform locking is chosen over a per-implementation
 * mix because the cost is a few nanoseconds on an uncontended lock, and the hot path is already
 * doing map lookups.
 *
 * Contention is naturally low: the lock is per attribute set, not per instrument or global, so
 * concurrent requests only contend when they share dimensions.
 */
internal interface Aggregator {
    /**
     * Read the accumulated value **and reset** for the next interval.
     *
     * Read-and-reset in one locked operation, not two calls, so that measurements arriving
     * concurrently with a collection are either fully included or fully carried into the next
     * interval — never counted twice and never lost.
     *
     * @return the aggregated value, or null when nothing was recorded since the last call. Null means
     *   "omit this series entirely" rather than "publish zero": zeros for idle instruments would be
     *   billed as backend metrics and would flatten charts, hiding the distinction between "no
     *   traffic" and "traffic with a zero result".
     */
    fun collect(): MetricValue?
}

/**
 * Delta-temporality sum, backing both `MonotonicCounter` and `UpDownCounter`.
 *
 * Delta rather than cumulative because backends aggregate within each period: publishing a running
 * total would make an idle counter appear to keep accruing. This deliberately differs from
 * OpenTelemetry's default temporality, and is the kind of difference that silently produces wrong
 * dashboards rather than errors, so it is asserted in the aggregation tests.
 *
 * @param monotonic true for `MonotonicCounter`. Used both to reject negative deltas and to label the
 *   resulting [MetricValue.Sum] for exporters that distinguish the two kinds.
 */
internal class SumAggregator(private val monotonic: Boolean) : Aggregator {
    private val lock = reentrantLock()
    private var sum = 0.0

    // Tracked separately from `sum == 0.0` because zero is a legitimate delta for an UpDownCounter
    // that went up and back down. Without this flag such an interval would be indistinguishable from
    // "no activity" and would be silently omitted.
    private var dirty = false

    fun add(value: Double): Unit = lock.withLock {
        // A negative value on a monotonic counter violates the telemetry-api contract. Ignoring it is
        // chosen over throwing: this runs inside the caller's SDK operation, and a contract violation
        // in instrumentation must not fail the user's request. Ignoring is also chosen over taking
        // the absolute value, which would corrupt the metric while looking healthy.
        if (monotonic && value < 0) return@withLock
        sum += value
        dirty = true
    }

    override fun collect(): MetricValue? = lock.withLock {
        if (!dirty) return@withLock null
        MetricValue.Sum(sum, monotonic).also {
            sum = 0.0
            dirty = false
        }
    }
}

/**
 * Holds the most recent **absolute** value, for gauges and async up/down counters.
 *
 * Populated during collection from a callback's `record(...)`, not from the hot path — async
 * instruments are pull-based by contract.
 *
 * The value is never diffed against the previous interval. See [MetricValue.LastValue] for why that
 * distinction matters.
 */
internal class LastValueAggregator : Aggregator {
    private val lock = reentrantLock()

    // Nullable rather than defaulting to 0.0: a gauge whose callback did not run, or threw, must
    // publish nothing. Reporting 0.0 would look like "the queue is empty" instead of "unknown",
    // which is actively misleading during an incident.
    private var current: Double? = null

    fun set(value: Double): Unit = lock.withLock { current = value }

    override fun collect(): MetricValue? = lock.withLock {
        // Cleared after reading so that a gauge which stops reporting goes absent rather than
        // flatlining at a stale value forever.
        current?.let { MetricValue.LastValue(it) }.also { current = null }
    }
}

/**
 * Fixed-memory histogram aggregation — count, sum, min and max only. Maps to a CloudWatch
 * `StatisticSet`.
 *
 * **The default for every histogram.** Its footprint is four numbers per attribute set regardless of
 * how many measurements or how many distinct values arrive, which is what makes it safe to enable
 * unconditionally. Enabling [DistributionAggregator] by default would make heap usage a function of
 * request volume.
 *
 * The cost of that bound is that backends cannot derive percentiles from a statistic set (except in
 * degenerate single-sample cases), so p99 requires opting the instrument into `detailedMetrics`.
 */
internal class SummaryAggregator : Aggregator {
    private val lock = reentrantLock()
    private var count = 0L
    private var sum = 0.0

    // Seeded to the opposite extremes so the first recorded value always wins both comparisons.
    // -Double.MAX_VALUE rather than Double.MIN_VALUE: the latter is the smallest *positive* value,
    // which would make max wrong for any all-negative series. This is a classic slip and the reason
    // for the explicit note.
    private var min = Double.MAX_VALUE
    private var max = -Double.MAX_VALUE

    fun record(value: Double): Unit = lock.withLock {
        count++
        sum += value
        if (value < min) min = value
        if (value > max) max = value
    }

    override fun collect(): MetricValue? = lock.withLock {
        // count == 0 is the only reliable emptiness test: sum can legitimately be 0.0.
        if (count == 0L) return@withLock null
        MetricValue.Summary(count, sum, min, max).also {
            count = 0
            sum = 0.0
            min = Double.MAX_VALUE
            max = -Double.MAX_VALUE
        }
    }
}

/**
 * Retains distinct values with their occurrence counts, enabling backend-computed percentiles.
 *
 * **Opt-in per instrument**, via `detailedMetrics`, and bounded. Both properties are deliberate:
 *
 * - *Opt-in*, because memory grows with the number of distinct values and a latency histogram's
 *   values are near-unique. An unbounded map would grow with request volume between publishes.
 * - *Bounded*, because [CardinalityGuard] does not cover this axis — it bounds the number of
 *   attribute sets, not the number of distinct values inside one. Without a second limit here,
 *   enabling `detailedMetrics` on a busy instrument would be an OOM waiting to happen, which would
 *   make the feature unsafe to document.
 *
 * @param maxDistinctValues distinct values retained per attribute set. Beyond this, new values are
 *   discarded and the result is flagged truncated. Discarding *new* values rather than evicting old
 *   ones keeps the operation O(1) and avoids the eviction policy question; the trade-off is a bias
 *   toward values seen early in the interval, which is acceptable because truncation is surfaced
 *   rather than hidden.
 */
internal class DistributionAggregator(
    private val maxDistinctValues: Int = DEFAULT_MAX_DISTINCT_VALUES,
) : Aggregator {
    private val lock = reentrantLock()
    private var values = mutableMapOf<Double, Long>()
    private var truncated = false

    fun record(value: Double): Unit = lock.withLock {
        val existing = values[value]
        when {
            // An already-tracked value is always countable, even past the cap: incrementing an
            // existing entry adds no memory, so refusing it would lose data for nothing.
            existing != null -> values[value] = existing + 1
            values.size < maxDistinctValues -> values[value] = 1
            else -> truncated = true
        }
    }

    override fun collect(): MetricValue? = lock.withLock {
        if (values.isEmpty()) return@withLock null
        MetricValue.Distribution(values, truncated).also {
            // Replaced with a fresh map rather than cleared: the old map is handed to the exporter by
            // reference, and clearing it here would empty the snapshot the exporter is about to read.
            values = mutableMapOf()
            truncated = false
        }
    }

    internal companion object {
        /**
         * 10,000 distinct values per attribute set.
         *
         * Roughly 0.5 MB of map overhead per attribute set at the limit — large enough that a normal
         * latency histogram is captured in full for a one-minute interval, small enough that hitting
         * the cap across many attribute sets is survivable rather than fatal.
         */
        const val DEFAULT_MAX_DISTINCT_VALUES: Int = 10_000
    }
}
