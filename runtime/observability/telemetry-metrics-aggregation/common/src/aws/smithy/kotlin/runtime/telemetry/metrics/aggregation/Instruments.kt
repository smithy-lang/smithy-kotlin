/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.metrics.AsyncMeasurementHandle
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.Histogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter
import aws.smithy.kotlin.runtime.telemetry.metrics.UpDownCounter

/*
 * Instrument implementations, called on every operation and so deliberately thin: resolve the aggregator,
 * hand off the value, return. Anything expensive belongs in the collection path, which runs once per interval
 * rather than once per request.
 */

/**
 * `MonotonicCounter` backed by a delta [SumAggregator].
 *
 * @see MetricValue.Sum for why the aggregation is delta rather than cumulative.
 */
internal class AggregatingMonotonicCounter(private val instrument: SyncInstrument) : MonotonicCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        // The telemetry-api contract requires a positive value. Returned silently rather than thrown, since
        // this runs inside the caller's SDK operation; the aggregator rejects it again.
        if (value < 0) return
        instrument.aggregatorFor<SumAggregator>(attributes).add(value.toDouble())
    }

    // `context` is ignored: it correlates a measurement with an active span for tracing exemplars, which
    // backends such as CloudWatch cannot represent.
}

/**
 * `UpDownCounter` backed by a delta [SumAggregator]. This is the *synchronous* up-down counter, which is
 * delta; its asynchronous namesake is absolute - see [MetricValue.LastValue].
 */
internal class AggregatingUpDownCounter(private val instrument: SyncInstrument) : UpDownCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        // No sign check: negative deltas are the entire point of an up-down counter.
        instrument.aggregatorFor<SumAggregator>(attributes).add(value.toDouble())
    }
}

/**
 * Histogram over [Long] or [Double], covering both `LongHistogram` and `DoubleHistogram`. One generic class,
 * since both widen to `Double` immediately and a `Long`-specialised path would duplicate every aggregator
 * for no accuracy gain at SDK magnitudes.
 *
 * @param instrument the aggregation state for this histogram.
 * @param fidelity resolved at construction from `detailedMetrics`, so the hot path is a `when` over an enum
 *   rather than a set lookup.
 */
internal class AggregatingHistogram<T : Number>(
    private val instrument: SyncInstrument,
    private val fidelity: HistogramFidelity,
) : Histogram<T> {
    override fun record(value: T, attributes: Attributes, context: Context?) {
        val v = value.toDouble()
        when (fidelity) {
            HistogramFidelity.SUMMARY -> instrument.aggregatorFor<SummaryAggregator>(attributes).record(v)
            HistogramFidelity.DISTRIBUTION -> instrument.aggregatorFor<DistributionAggregator>(attributes).record(v)
        }
    }
}

/*
 * Typed factories, only to pin the generic parameter: `T` cannot be inferred when the target type is the
 * `LongHistogram`/`DoubleHistogram` typealias.
 */

internal fun longHistogram(instrument: SyncInstrument, fidelity: HistogramFidelity): LongHistogram = AggregatingHistogram(instrument, fidelity)

internal fun doubleHistogram(instrument: SyncInstrument, fidelity: HistogramFidelity): DoubleHistogram = AggregatingHistogram(instrument, fidelity)

/**
 * De-registers an async instrument, honouring the [AsyncMeasurementHandle] contract that a handle stops its
 * callback from being invoked. Holds the registry and an id rather than the instrument, so a stopped handle
 * does not pin aggregation state.
 */
internal class AggregatingAsyncMeasurementHandle(
    private val registry: InstrumentRegistry,
    private val id: Int,
) : AsyncMeasurementHandle {
    override fun stop(): Unit = registry.unregisterAsync(id)
}
