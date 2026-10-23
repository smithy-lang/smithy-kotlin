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

/** Which aggregation a histogram uses. Resolved once at instrument creation, not per `record()` call. */
internal enum class HistogramFidelity { SUMMARY, DISTRIBUTION }

/**
 * Everything the registry needs to decide aggregation and bound growth.
 *
 * @param dimensions attribute names promoted to backend dimensions. Everything else is discarded. An
 *   allowlist, so a newly added SDK attribute cannot silently increase cost on upgrade.
 * @param detailedMetrics instrument names that publish full distributions instead of summary
 *   statistics. Keyed by name so it can be configured before any instrument exists.
 *   See [DistributionAggregator].
 * @param maxCardinality distinct attribute sets per instrument before overflow bucketing begins.
 *   See [CardinalityGuard].
 * @param maxDistinctValues distinct values per attribute set for instruments in [detailedMetrics].
 *   [maxCardinality] bounds how many series exist; this bounds how large one series can get.
 */
public class AggregationConfig(
    public val dimensions: Set<String>,
    public val detailedMetrics: Set<String> = emptySet(),
    public val maxCardinality: Int = 1_000,
    public val maxDistinctValues: Int = DistributionAggregator.DEFAULT_MAX_DISTINCT_VALUES,
)

/**
 * Aggregation state for one synchronous instrument across all of its attribute sets.
 *
 * One instance per `(scope, name, units, description)`, created on first use and retained for the process
 * lifetime: callers may hold an instrument reference indefinitely, so evicting the state behind a live
 * reference would silently stop recording.
 *
 * @param descriptor identity of the instrument this state belongs to.
 * @param config dimension allowlist and cardinality bound.
 * @param newAggregator a factory because each attribute set accumulates independently.
 */
internal class SyncInstrument(
    val descriptor: InstrumentDescriptor,
    private val config: AggregationConfig,
    private val newAggregator: () -> Aggregator,
) {
    private val lock = reentrantLock()

    // Per-instrument, not shared: one high-cardinality instrument must not exhaust the allowance of
    // well-behaved ones.
    private val guard = CardinalityGuard(config.maxCardinality)
    private val byDimensions = mutableMapOf<DimensionKey, Aggregator>()

    /**
     * Resolve the aggregator for [attributes], creating it on first use.
     *
     * @return the aggregator, cast to the expected concrete type. Safe because [newAggregator] is fixed at
     *   construction, so an instrument's aggregator type cannot vary across attribute sets. Making this class
     *   generic instead would force the type parameter through the registry map, which is heterogeneous.
     */
    fun <A : Aggregator> aggregatorFor(attributes: Attributes): A {
        // Outside the lock: projection sorts and allocates, and depends only on the caller's own attributes.
        val requested = dimensionsOf(attributes, config.dimensions)
        return lock.withLock {
            // Under the lock, since two threads racing a near-cap check could otherwise both be admitted.
            val key = guard.admit(requested)

            @Suppress("UNCHECKED_CAST")
            byDimensions.getOrPut(key) { newAggregator() } as A
        }
    }

    /**
     * Drain every attribute set, resetting each aggregator. Entries are kept even when they yield null:
     * removing idle series would release their cardinality slots and let a churning workload re-admit new
     * sets forever, defeating the guard.
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
 * Async instruments are pull-based by contract - the callback runs at collection time and reports current
 * absolute values - which is why a push-only telemetry provider cannot support them.
 *
 * @param descriptor identity of the instrument this state belongs to.
 * @param config dimension allowlist and cardinality bound.
 * @param invoke adapts the three differently-typed telemetry-api callbacks to one shape, so this class needs
 *   no knowledge of which kind it holds.
 */
internal class AsyncInstrument(
    val descriptor: InstrumentDescriptor,
    private val config: AggregationConfig,
    private val invoke: (AsyncSink) -> Unit,
) {
    private val lock = reentrantLock()
    private val guard = CardinalityGuard(config.maxCardinality)

    /**
     * The recording sink handed to user callbacks. Absolute values only - never delta-ised.
     *
     * Kotlin forbids implementing `AsyncMeasurement<Long>` and `AsyncMeasurement<Double>` on one class, so the
     * shared store lives here and [asLong]/[asDouble] expose typed views over it.
     *
     * A fresh instance per collection cycle, so a callback that stops reporting an attribute set makes that
     * series go absent rather than repeat a stale value.
     */
    internal inner class AsyncSink {
        // Unsynchronized: a sink is confined to one collection cycle on one coroutine. The `guard` it consults
        // is shared across cycles, hence the lock below.
        val points = mutableMapOf<DimensionKey, Double>()

        fun put(value: Double, attributes: Attributes) {
            val key = lock.withLock { guard.admit(dimensionsOf(attributes, config.dimensions)) }
            // Overwrite, not accumulate: a callback recording twice in one cycle is correcting the current
            // value, not reporting two events.
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
     * The callback is opaque code running inside the collection loop, so a throw is contained here: only this
     * instrument is absent for the interval, and the reader's loop survives into the next cycle. `Exception`
     * rather than `Throwable`, since an `Error` means the process is already compromised.
     *
     * @param logger used at WARN, not ERROR: a broken gauge degrades observability without breaking the
     *   application.
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
 * Owns all instrument state for one [AggregatingMeterProvider], and is the single point collection reads.
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

    // Never reused: a recycled id would let a stale handle's stop() cancel a newer instrument.
    private var nextAsyncId = 0

    /**
     * Get or create the state for a synchronous instrument. Deduplicating by [descriptor] makes creation
     * idempotent, which matters because instrumentation code often calls `createMonotonicCounter` per
     * operation rather than caching it.
     *
     * @param descriptor identity of the instrument.
     * @param newAggregator evaluated only on first creation.
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
     * @return an opaque id for later de-registration - an id rather than the instrument, because registering
     *   `"queue.depth"` twice must yield two independently stoppable handles.
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
     * Instruments recording nothing are omitted rather than published as zero - see
     * [Aggregator.collect].
     */
    fun collectAll(): List<MetricData> {
        // Once for the whole cycle, and before collecting, so a slow gauge callback cannot push the batch's
        // timestamp past the interval it describes.
        val now = Instant.now()

        // Snapshot under the lock, then release it before collecting: async callbacks are opaque code that may
        // create instruments, re-entering `sync`/`registerAsync`.
        val syncSnapshot = lock.withLock { sync.values.toList() }
        val asyncSnapshot = lock.withLock { async.values.toList() }

        val out = mutableListOf<MetricData>()
        syncSnapshot.forEach { instrument ->
            instrument.collect().takeIf { it.isNotEmpty() }?.let {
                out += MetricData(instrument.descriptor, now, it)
            }
        }
        // After sync, so gauge callbacks - the only part of collection that can run arbitrarily slowly -
        // cannot delay draining aggregators that are still accumulating from live requests.
        asyncSnapshot.forEach { instrument ->
            instrument.collect(logger).takeIf { it.isNotEmpty() }?.let {
                out += MetricData(instrument.descriptor, now, it)
            }
        }
        return out
    }
}
