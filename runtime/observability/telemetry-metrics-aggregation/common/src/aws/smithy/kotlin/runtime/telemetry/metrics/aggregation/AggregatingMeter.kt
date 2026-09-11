/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.telemetry.metrics.AsyncMeasurementHandle
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleGaugeCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongGaugeCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.LongHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongUpDownCounterCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.Meter
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter
import aws.smithy.kotlin.runtime.telemetry.metrics.UpDownCounter

/**
 * [Meter] backed by the aggregation pipeline. All seven factories return working instruments, including real
 * handles from the async three rather than [AsyncMeasurementHandle.None].
 *
 * Stateless apart from its constructor arguments - state lives in [InstrumentRegistry] - so one instance can
 * be shared.
 *
 * @param scope the instrumentation scope, stamped onto every descriptor so exporters can attribute a metric
 *   to its source.
 * @param registry the shared instrument state this meter's instruments record into.
 */
internal class AggregatingMeter(
    private val scope: String,
    private val registry: InstrumentRegistry,
) : Meter {

    /** Bundles the four identity fields, so `scope` cannot be forgotten at one of seven call sites. */
    private fun descriptor(name: String, units: String?, description: String?) = InstrumentDescriptor(scope, name, units, description)

    override fun createMonotonicCounter(name: String, units: String?, description: String?): MonotonicCounter = AggregatingMonotonicCounter(registry.sync(descriptor(name, units, description)) { SumAggregator(monotonic = true) })

    override fun createUpDownCounter(name: String, units: String?, description: String?): UpDownCounter = AggregatingUpDownCounter(registry.sync(descriptor(name, units, description)) { SumAggregator(monotonic = false) })

    override fun createLongHistogram(name: String, units: String?, description: String?): LongHistogram {
        val fidelity = registry.histogramFidelity(name)
        return longHistogram(histogramInstrument(name, units, description, fidelity), fidelity)
    }

    override fun createDoubleHistogram(name: String, units: String?, description: String?): DoubleHistogram {
        val fidelity = registry.histogramFidelity(name)
        return doubleHistogram(histogramInstrument(name, units, description, fidelity), fidelity)
    }

    /**
     * Shared by the `Long` and `Double` histogram factories, which differ only in the typed wrapper returned.
     *
     * The aggregator factory is a lambda so it runs only on first creation; repeat calls reuse the existing
     * state. An instrument's aggregation is therefore fixed once created, since switching it on a live
     * instrument would discard accumulated data mid-interval.
     */
    private fun histogramInstrument(
        name: String,
        units: String?,
        description: String?,
        fidelity: HistogramFidelity,
    ) = registry.sync(descriptor(name, units, description)) {
        when (fidelity) {
            HistogramFidelity.SUMMARY -> SummaryAggregator()
            // Via the registry, so the configured maxDistinctValues applies rather than the default.
            HistogramFidelity.DISTRIBUTION -> registry.newDistribution()
        }
    }

    /*
     * The three async factories, each adapting its differently-typed callback to the registry's single untyped
     * shape. createAsyncUpDownCounter takes a Long callback because the telemetry API has no Double variant.
     */

    override fun createLongGauge(
        name: String,
        callback: LongGaugeCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = registerAsync(name, units, description) { callback(it.asLong()) }

    override fun createDoubleGauge(
        name: String,
        callback: DoubleGaugeCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = registerAsync(name, units, description) { callback(it.asDouble()) }

    override fun createAsyncUpDownCounter(
        name: String,
        callback: LongUpDownCounterCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = registerAsync(name, units, description) { callback(it.asLong()) }

    /**
     * Register and wrap the returned id in a handle. Never invokes [invoke]: sampling at registration would
     * produce a measurement outside any collection cycle, timestamped at startup before the measured value is
     * meaningful.
     */
    private fun registerAsync(
        name: String,
        units: String?,
        description: String?,
        invoke: (AsyncInstrument.AsyncSink) -> Unit,
    ): AsyncMeasurementHandle {
        val id = registry.registerAsync(descriptor(name, units, description), invoke)
        return AggregatingAsyncMeasurementHandle(registry, id)
    }
}
