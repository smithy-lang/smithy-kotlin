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
 * [Meter] backed by the aggregation pipeline.
 *
 * Every one of the seven factories on [Meter] returns a working instrument. In particular the three
 * async factories return real handles rather than [AsyncMeasurementHandle.None]; silently dropping
 * gauges and async up-down counters is the defect this module exists to avoid, and a conformance test
 * over all seven factories guards against it regressing.
 *
 * Stateless apart from its two constructor arguments — all state lives in [InstrumentRegistry] — so it
 * is safe to hand the same instance to any number of callers.
 *
 * @param scope the instrumentation scope this meter was created for. Retained and stamped onto every
 *   descriptor rather than discarded, so exporters can attribute a metric to its source.
 * @param registry the shared instrument state this meter's instruments record into.
 */
internal class SdkMeter(
    private val scope: String,
    private val registry: InstrumentRegistry,
) : Meter {

    /** Bundles the four identity fields, so `scope` cannot be forgotten at one of seven call sites. */
    private fun descriptor(name: String, units: String?, description: String?) = InstrumentDescriptor(scope, name, units, description)

    override fun createMonotonicCounter(name: String, units: String?, description: String?): MonotonicCounter = SdkMonotonicCounter(registry.sync(descriptor(name, units, description)) { SumAggregator(monotonic = true) })

    override fun createUpDownCounter(name: String, units: String?, description: String?): UpDownCounter = SdkUpDownCounter(registry.sync(descriptor(name, units, description)) { SumAggregator(monotonic = false) })

    override fun createLongHistogram(name: String, units: String?, description: String?): LongHistogram {
        val fidelity = registry.histogramFidelity(name)
        return longHistogram(histogramInstrument(name, units, description, fidelity), fidelity)
    }

    override fun createDoubleHistogram(name: String, units: String?, description: String?): DoubleHistogram {
        val fidelity = registry.histogramFidelity(name)
        return doubleHistogram(histogramInstrument(name, units, description, fidelity), fidelity)
    }

    /**
     * Shared by the `Long` and `Double` histogram factories, which differ only in the typed wrapper they
     * return.
     *
     * The aggregator factory is a lambda so it runs only when the instrument is first created; on repeat
     * calls the existing state is reused and no aggregator is allocated. Note the consequence: if an
     * instrument was first created before `detailedMetrics` was reconsidered, its aggregation is already
     * fixed. That is intentional — switching aggregation on a live instrument would discard accumulated
     * data mid-interval.
     */
    private fun histogramInstrument(
        name: String,
        units: String?,
        description: String?,
        fidelity: HistogramFidelity,
    ) = registry.sync(descriptor(name, units, description)) {
        when (fidelity) {
            HistogramFidelity.SUMMARY -> SummaryAggregator()
            // Routed through the registry so the configured maxDistinctValues bound is applied.
            // Constructing DistributionAggregator() directly here would silently take the default bound
            // and quietly ignore the user's setting.
            HistogramFidelity.DISTRIBUTION -> registry.newDistribution()
        }
    }

    /*
     * The three async factories. Each adapts its differently-typed callback to the registry's single
     * untyped shape, which is why AsyncSink exposes asLong()/asDouble() views rather than implementing
     * both AsyncMeasurement types itself.
     *
     * Note createAsyncUpDownCounter takes a Long callback: the telemetry API has no
     * createDoubleUpDownCounter, so there is no Double variant to implement here.
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
     * Register and wrap the returned id in a handle.
     *
     * Note what this does *not* do: it never invokes [invoke]. Sampling a gauge at registration would
     * produce a measurement outside any collection cycle, timestamped whenever the application happened
     * to construct its instruments — typically at startup, before the value being measured is
     * meaningful.
     */
    private fun registerAsync(
        name: String,
        units: String?,
        description: String?,
        invoke: (AsyncInstrument.AsyncSink) -> Unit,
    ): AsyncMeasurementHandle {
        val id = registry.registerAsync(descriptor(name, units, description), invoke)
        return SdkAsyncMeasurementHandle(registry, id)
    }
}
