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
 * Instrument implementations.
 *
 * These are the objects instrumentation code holds and calls on every operation, so they are
 * deliberately thin: resolve the aggregator, hand off the value, return. No suspension, no I/O, no
 * allocation beyond what dimension projection requires. Anything expensive belongs in the collection
 * path, which runs once per interval rather than once per request.
 *
 * Every instrument here is functional. There are no no-op implementations: an instrument that accepts
 * measurements and silently discards them is the specific defect this module exists to avoid.
 */

/**
 * `MonotonicCounter` backed by a delta [SumAggregator].
 *
 * @see MetricValue.Sum for why the aggregation is delta rather than cumulative.
 */
internal class SdkMonotonicCounter(private val instrument: SyncInstrument) : MonotonicCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        // The telemetry-api contract states the value MUST be positive. Returning silently rather than
        // throwing: this executes inside the caller's SDK operation, and broken instrumentation must not
        // fail a user's request. The aggregator rejects it again as a second line of defence, since it
        // can also be reached through other paths.
        if (value < 0) return
        instrument.aggregatorFor<SumAggregator>(attributes).add(value.toDouble())
    }

    // `context` is accepted and ignored. It exists in the API to correlate a measurement with an active
    // span, which is a tracing-exemplar feature; backends such as CloudWatch have no way to represent
    // exemplars, so there is nothing useful to do with it here. Kept in the signature because the
    // interface requires it, and because an exporter that can use it may exist later.
}

/**
 * `UpDownCounter` backed by a delta [SumAggregator].
 *
 * Note this is the *synchronous* up-down counter, which is delta. Its asynchronous namesake is
 * absolute — see [MetricValue.LastValue]. Conflating the two is the easiest bug in this module.
 */
internal class SdkUpDownCounter(private val instrument: SyncInstrument) : UpDownCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        // No sign check: negative deltas are the entire point of an up-down counter.
        instrument.aggregatorFor<SumAggregator>(attributes).add(value.toDouble())
    }
}

/**
 * Histogram over [Long] or [Double], covering both `LongHistogram` and `DoubleHistogram`.
 *
 * One generic class rather than two, since the only difference is the input type and both widen to
 * `Double` immediately. Widening at the boundary keeps a single aggregation implementation: a
 * `Long`-specialised path would duplicate every aggregator for no accuracy gain at the magnitudes SDK
 * metrics deal in (durations, byte counts, retry counts).
 *
 * @param instrument the aggregation state for this histogram.
 * @param fidelity resolved at construction from `detailedMetrics`, not per call, so the hot path is a
 *   `when` over an enum rather than a set lookup.
 */
internal class SdkHistogram<T : Number>(
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
 * Typed factories. These exist only to pin the generic parameter: `SdkHistogram(...)` cannot infer `T`
 * from context when the target type is the `LongHistogram`/`DoubleHistogram` typealias, and spelling
 * out `SdkHistogram<Long>(...)` at each call site in SdkMeter reads worse.
 */

internal fun longHistogram(instrument: SyncInstrument, fidelity: HistogramFidelity): LongHistogram = SdkHistogram(instrument, fidelity)

internal fun doubleHistogram(instrument: SyncInstrument, fidelity: HistogramFidelity): DoubleHistogram = SdkHistogram(instrument, fidelity)

/**
 * De-registers an async instrument, honouring the documented [AsyncMeasurementHandle] contract that the
 * handle "can be used for de-registering the counter and stopping the callback from being invoked."
 *
 * Returning a real handle here — rather than [AsyncMeasurementHandle.None] — is what makes gauge
 * de-registration actually work. Holding the registry and an id, rather than the instrument itself,
 * keeps the handle from pinning aggregation state alive after `stop()`.
 */
internal class SdkAsyncMeasurementHandle(
    private val registry: InstrumentRegistry,
    private val id: Int,
) : AsyncMeasurementHandle {
    override fun stop(): Unit = registry.unregisterAsync(id)
}
