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
 * Implementations lock rather than using atomics: [SummaryAggregator] must update four fields as one unit,
 * since a torn read yielding `min > max` publishes an arithmetically impossible statistic set that backends
 * reject. Contention is low because the lock is per attribute set, so concurrent requests contend only when
 * they share dimensions.
 */
internal interface Aggregator {
    /**
     * Read the accumulated value **and reset** for the next interval, in one locked operation so a
     * measurement arriving during a collection is either fully included or fully carried forward.
     *
     * @return the aggregated value, or null when nothing was recorded since the last call - meaning omit the
     *   series, not publish zero. Zeros for idle instruments are billed and flatten charts, hiding the
     *   difference between no traffic and a zero result.
     */
    fun collect(): MetricValue?
}

/**
 * Delta-temporality sum, backing both `MonotonicCounter` and `UpDownCounter`. Delta rather than cumulative
 * because backends aggregate within each period, so a running total would make an idle counter appear to
 * keep accruing. This differs from OpenTelemetry's default temporality.
 *
 * @param monotonic true for `MonotonicCounter`. Rejects negative deltas and labels the resulting
 *   [MetricValue.Sum] for exporters that distinguish the two kinds.
 */
internal class SumAggregator(private val monotonic: Boolean) : Aggregator {
    private val lock = reentrantLock()
    private var sum = 0.0

    // Separate from `sum == 0.0` because zero is a legitimate delta for an UpDownCounter that went up and
    // back down, and would otherwise be indistinguishable from no activity.
    private var dirty = false

    fun add(value: Double): Unit = lock.withLock {
        // Ignored rather than thrown: this runs inside the caller's SDK operation, and an instrumentation
        // contract violation must not fail their request.
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
 * Holds the most recent **absolute** value, for gauges and async up/down counters. Populated during
 * collection from a callback's `record(...)`, not from the hot path, since async instruments are pull-based.
 * Never diffed against the previous interval - see [MetricValue.LastValue].
 */
internal class LastValueAggregator : Aggregator {
    private val lock = reentrantLock()

    // Nullable rather than 0.0: a gauge whose callback did not run must publish nothing, since 0.0 would
    // read as "the queue is empty" rather than "unknown".
    private var current: Double? = null

    fun set(value: Double): Unit = lock.withLock { current = value }

    override fun collect(): MetricValue? = lock.withLock {
        // Cleared after reading so a gauge that stops reporting goes absent rather than flatlining at a
        // stale value.
        current?.let { MetricValue.LastValue(it) }.also { current = null }
    }
}

/**
 * Fixed-memory histogram aggregation - count, sum, min and max only, mapping to a CloudWatch
 * `StatisticSet`.
 *
 * The default for every histogram: four numbers per attribute set however many measurements arrive, whereas
 * [DistributionAggregator] by default would make heap usage a function of request volume. Backends cannot
 * derive percentiles from a statistic set, so p99 requires opting the instrument into `detailedMetrics`.
 */
internal class SummaryAggregator : Aggregator {
    private val lock = reentrantLock()
    private var count = 0L
    private var sum = 0.0

    // Seeded to opposite extremes so the first value wins both comparisons. `-Double.MAX_VALUE`, not
    // `Double.MIN_VALUE`, which is the smallest *positive* value and would make max wrong for an
    // all-negative series.
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
 * Opt-in per instrument via `detailedMetrics`, because memory grows with the number of distinct values and a
 * latency histogram's are near-unique. Bounded as well, since [CardinalityGuard] bounds attribute sets, not
 * distinct values within one.
 *
 * @param maxDistinctValues distinct values retained per attribute set; beyond this new values are discarded
 *   and the result flagged truncated. Discarding new rather than evicting old keeps this O(1), at the cost of
 *   a bias toward values seen early in the interval.
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
            // Countable even past the cap: incrementing an existing entry adds no memory.
            existing != null -> values[value] = existing + 1
            values.size < maxDistinctValues -> values[value] = 1
            else -> truncated = true
        }
    }

    override fun collect(): MetricValue? = lock.withLock {
        if (values.isEmpty()) return@withLock null
        MetricValue.Distribution(values, truncated).also {
            // Replaced, not cleared: the old map goes to the exporter by reference, so clearing it would
            // empty the snapshot it is about to read.
            values = mutableMapOf()
            truncated = false
        }
    }

    internal companion object {
        /**
         * 10,000 distinct values per attribute set - roughly 0.5 MB of map overhead at the limit. Enough to
         * capture a normal latency histogram in full for a one-minute interval.
         */
        const val DEFAULT_MAX_DISTINCT_VALUES: Int = 10_000
    }
}
