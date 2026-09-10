/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleAsyncMeasurement
import aws.smithy.kotlin.runtime.telemetry.metrics.LongAsyncMeasurement
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * Which aggregation a histogram uses.
 *
 * Resolved once, when the instrument is created, rather than per measurement — the decision depends
 * only on configuration, and re-deriving it on the hot path would mean a set lookup per `record()`
 * call.
 */
internal enum class HistogramFidelity { SUMMARY, DISTRIBUTION }

/**
 * Everything the registry needs to decide aggregation and bound growth.
 *
 * Grouped into one type rather than threaded as separate parameters because it is passed down three
 * levels (provider to registry to instrument) and every level needs a different subset.
 *
 * @param dimensions attribute names promoted to backend dimensions. Everything else is discarded.
 *   An allowlist rather than a denylist, because the safe default must be the *narrow* one: backends
 *   bill per unique dimension combination, so a denylist would make every newly added SDK attribute a
 *   silent cost increase on upgrade.
 * @param detailedMetrics instrument names that publish full distributions instead of summary
 *   statistics. Keyed by name rather than by instrument reference so it can be configured before any
 *   instrument exists. Empty by default: percentiles cost memory and requests, so they are opted into
 *   per instrument. See [DistributionAggregator].
 * @param maxCardinality distinct attribute sets per instrument before overflow bucketing begins. 1000
 *   by default — high enough that a normal service/operation breakdown never approaches it, low enough
 *   to cap worst-case heap and spend at a survivable level. See [CardinalityGuard].
 * @param maxDistinctValues distinct values per attribute set for instruments in [detailedMetrics].
 *   The second, independent unboundedness axis: [maxCardinality] bounds how many series exist, this
 *   bounds how large one series' accumulator can get.
 */
public data class AggregationConfig(
    public val dimensions: Set<String>,
    public val detailedMetrics: Set<String> = emptySet(),
    public val maxCardinality: Int = 1_000,
    public val maxDistinctValues: Int = DistributionAggregator.DEFAULT_MAX_DISTINCT_VALUES,
)

/**
 * Aggregation state for one synchronous instrument across all of its attribute sets.
 *
 * One instance per `(scope, name, units, description)`; created on first use and retained for the
 * process lifetime. Instruments are not evicted, because the telemetry API lets callers hold an
 * instrument reference indefinitely and evicting the state behind a live reference would silently stop
 * recording.
 *
 * @param descriptor identity of the instrument this state belongs to.
 * @param config dimension allowlist and cardinality bound.
 * @param newAggregator factory rather than a fixed instance, because a new aggregator is needed per
 *   attribute set — each series accumulates independently.
 */
internal class SyncInstrument(
    val descriptor: InstrumentDescriptor,
    private val config: AggregationConfig,
    private val newAggregator: () -> Aggregator,
) {
    private val lock = reentrantLock()

    // Per-instrument rather than shared: the cap is "distinct attribute sets for this metric", so one
    // high-cardinality instrument must not exhaust the allowance of well-behaved ones.
    private val guard = CardinalityGuard(config.maxCardinality)
    private val byDimensions = mutableMapOf<DimensionKey, Aggregator>()

    /**
     * Resolve the aggregator for [attributes], creating it on first use.
     *
     * @return the aggregator, cast to the expected concrete type. The cast is safe because
     *   [newAggregator] is fixed at construction and every entry in the map came from it, so an
     *   instrument's aggregator type cannot vary across attribute sets. The alternative — making
     *   [SyncInstrument] generic — would force the type parameter through the registry map and buy
     *   nothing, since the registry is heterogeneous by nature.
     */
    fun <A : Aggregator> aggregatorFor(attributes: Attributes): A {
        // Computed outside the lock: dimension projection sorts and allocates, and it depends only on
        // the caller's own attributes. Holding the lock across it would serialize unrelated requests
        // on the hot path for no reason.
        val requested = dimensionsOf(attributes, config.dimensions)
        return lock.withLock {
            // Admission happens under the lock because the guard's `seen` set is mutable shared state,
            // and two threads racing a near-cap check could otherwise both be admitted.
            val key = guard.admit(requested)

            @Suppress("UNCHECKED_CAST")
            byDimensions.getOrPut(key) { newAggregator() } as A
        }
    }

    /**
     * Drain every attribute set, resetting each aggregator.
     *
     * Entries are kept in [byDimensions] after collection even when they yield null. Removing idle
     * series would look like a memory win, but it would also release their cardinality slots and let a
     * churning workload re-admit new sets forever, defeating the guard.
     */
    fun collect(): List<MetricPoint> = lock.withLock {
        byDimensions.mapNotNull { (key, agg) ->
            agg.collect()?.let { MetricPoint(key.dimensions, it) }
        }
    }
}

/**
 * A registered async instrument: the user's callback plus the sink it records into.
 *
 * Async instruments are pull-based by contract — the callback is invoked at collection time and
 * reports current absolute values. That is precisely why a push-only telemetry provider cannot
 * support them: with no collection loop, there is no moment at which to pull.
 *
 * @param descriptor identity of the instrument this state belongs to.
 * @param config dimension allowlist and cardinality bound.
 * @param invoke adapts the three differently-typed telemetry-api callbacks (`LongGaugeCallback`,
 *   `DoubleGaugeCallback`, `LongUpDownCounterCallback`) to one shape, so this class needs no knowledge
 *   of which kind it holds.
 */
internal class AsyncInstrument(
    val descriptor: InstrumentDescriptor,
    private val config: AggregationConfig,
    private val invoke: (AsyncSink) -> Unit,
) {
    private val lock = reentrantLock()
    private val guard = CardinalityGuard(config.maxCardinality)

    /**
     * The recording sink handed to user callbacks. Absolute values only — never delta-ised.
     *
     * Kotlin forbids implementing `AsyncMeasurement<Long>` and `AsyncMeasurement<Double>` on one class
     * (the same generic interface cannot be implemented twice with different type arguments), so the
     * shared store lives here and [asLong]/[asDouble] expose the two typed views over it.
     *
     * A fresh instance per collection cycle, not a reusable one: that makes each cycle's snapshot
     * independent, so a callback that stops reporting an attribute set causes that series to go absent
     * rather than repeat a stale value.
     */
    internal inner class AsyncSink {
        // Not synchronized, because a sink is confined to a single collection cycle on a single
        // coroutine. The `guard` it consults *is* shared across cycles, hence the lock below.
        val points = mutableMapOf<DimensionKey, Double>()

        fun put(value: Double, attributes: Attributes) {
            val key = lock.withLock { guard.admit(dimensionsOf(attributes, config.dimensions)) }
            // Overwrite, not accumulate: a callback recording the same attribute set twice in one
            // cycle is reporting a corrected current value, not two events to be summed.
            points[key] = value
        }

        fun asLong(): LongAsyncMeasurement = object : LongAsyncMeasurement {
            override fun record(value: Long, attributes: Attributes, context: Context?): Unit = put(value.toDouble(), attributes)
        }

        fun asDouble(): DoubleAsyncMeasurement = object : DoubleAsyncMeasurement {
            override fun record(value: Double, attributes: Attributes, context: Context?): Unit = put(value, attributes)
        }
    }

    /**
     * Invoke the callback and harvest whatever it recorded.
     *
     * ### Fault isolation
     *
     * The callback is opaque code — user-supplied or from another library — running inside the
     * collection loop. A throw is contained here so that:
     *
     * - only this instrument is absent for the interval; every other instrument still publishes;
     * - the reader's loop survives into the next cycle. Without this catch, one bad gauge would end
     *   metrics collection for the remaining process lifetime.
     *
     * `Exception` rather than `Throwable`: `Error` (OOM, stack overflow) indicates the process is
     * already compromised and must not be swallowed by telemetry code. `CancellationException` is not
     * a concern here because the callback is non-suspending by signature.
     *
     * @param logger used at WARN rather than ERROR: a broken gauge degrades observability but does not
     *   break the application, and ERROR would page someone for it.
     */
    fun collect(logger: Logger): List<MetricPoint> {
        val sink = AsyncSink()
        return try {
            invoke(sink)
            sink.points.map { (key, value) -> MetricPoint(key.dimensions, MetricValue.LastValue(value)) }
        } catch (e: Exception) {
            logger.warn(e) { "gauge callback for '${descriptor.name}' failed; skipping this interval" }
            emptyList()
        }
    }
}

/**
 * Owns all instrument state for one [SdkMeterProvider], and is the single point collection reads.
 *
 * Sync and async instruments are held in separate maps because they are keyed differently and
 * collected differently: sync instruments are deduplicated by descriptor so repeated
 * `createMonotonicCounter("calls")` calls share state, while async instruments are keyed by a unique
 * id so identically-named gauges remain independently stoppable.
 */
internal class InstrumentRegistry(
    private val config: AggregationConfig,
    private val logger: Logger,
) {
    private val lock = reentrantLock()
    private val sync = mutableMapOf<InstrumentDescriptor, SyncInstrument>()
    private val async = mutableMapOf<Int, AsyncInstrument>()

    // Monotonic, never reused. Reusing ids after removal would let a stale handle's stop() cancel a
    // different, newer instrument that happened to be assigned the recycled id.
    private var nextAsyncId = 0

    /**
     * Get or create the state for a synchronous instrument.
     *
     * Deduplicating by [descriptor] is what makes instrument creation idempotent: instrumentation code
     * frequently calls `createMonotonicCounter` per operation rather than caching it, and without this
     * each call would get its own accumulator and publish a partial series.
     *
     * @param descriptor identity of the instrument.
     * @param newAggregator evaluated only on first creation, so the caller can pass a factory whose
     *   choice depends on configuration without paying for it on every lookup.
     */
    fun sync(descriptor: InstrumentDescriptor, newAggregator: () -> Aggregator): SyncInstrument = lock.withLock { sync.getOrPut(descriptor) { SyncInstrument(descriptor, config, newAggregator) } }

    /** Resolve a histogram's aggregation from configuration. Called once per instrument creation. */
    fun histogramFidelity(name: String): HistogramFidelity = if (name in config.detailedMetrics) HistogramFidelity.DISTRIBUTION else HistogramFidelity.SUMMARY

    /** Factory so the per-instrument distinct-value bound is applied consistently. */
    fun newDistribution(): Aggregator = DistributionAggregator(config.maxDistinctValues)

    /**
     * Register an async instrument.
     *
     * @param descriptor identity of the instrument.
     * @param invoke the adapted callback to run at collection time.
     * @return an opaque id for later de-registration. An id rather than the [AsyncInstrument] itself,
     *   because two gauges can share the same name and descriptor — a caller registering
     *   `"queue.depth"` twice must get two independently stoppable handles, which descriptor-keyed
     *   storage could not express.
     */
    fun registerAsync(descriptor: InstrumentDescriptor, invoke: (AsyncInstrument.AsyncSink) -> Unit): Int = lock.withLock {
        val id = nextAsyncId++
        async[id] = AsyncInstrument(descriptor, config, invoke)
        id
    }

    /** Idempotent by virtue of `Map.remove`: a double `stop()` is harmless, per the handle contract. */
    fun unregisterAsync(id: Int) {
        lock.withLock { async.remove(id) }
    }

    /**
     * Collect every instrument: synchronous aggregators are drained and reset, async callbacks are
     * invoked to sample current values.
     *
     * Instruments recording nothing are omitted rather than published as zero — see
     * [Aggregator.collect].
     */
    fun collectAll(): List<MetricData> {
        // Stamped once for the whole cycle so every point shares a timestamp and lines up on a chart.
        // Taken before collection, not after, so a slow gauge callback cannot push the batch's
        // timestamp forward past the interval it actually describes.
        val now = Instant.now()

        // Snapshot the instrument lists under the lock, then release it before collecting. Holding the
        // registry lock across collection would be a deadlock risk: async callbacks are opaque code
        // that may create new instruments, which re-enters `sync`/`registerAsync`. Copying is cheap
        // because the number of instruments is small and stable.
        val syncSnapshot = lock.withLock { sync.values.toList() }
        val asyncSnapshot = lock.withLock { async.values.toList() }

        val out = mutableListOf<MetricData>()
        syncSnapshot.forEach { instrument ->
            instrument.collect().takeIf { it.isNotEmpty() }?.let {
                out += MetricData(instrument.descriptor, now, it)
            }
        }
        // Async collected after sync so that gauge callbacks — the only part of collection that can run
        // arbitrarily slowly — cannot delay draining the sync aggregators, which are still accumulating
        // from live requests.
        asyncSnapshot.forEach { instrument ->
            instrument.collect(logger).takeIf { it.isNotEmpty() }?.let {
                out += MetricData(instrument.descriptor, now, it)
            }
        }
        return out
    }
}
