/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.util

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.AbstractTelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.context.AbstractContext
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.metrics.AbstractMeter
import aws.smithy.kotlin.runtime.telemetry.metrics.AbstractMeterProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.Histogram
import aws.smithy.kotlin.runtime.telemetry.metrics.Meter
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter

/**
 * A single recorded metric measurement captured by [RecordingTelemetryProvider].
 *
 * @param name the instrument name (e.g. `smithy.client.call.request_payload_size`)
 * @param value the recorded value (counter deltas are converted to [Double])
 * @param attributes the attributes associated with the measurement
 * @param context the telemetry context associated with the measurement (or `null` if none was passed)
 */
internal data class MetricRecord(
    val name: String,
    val value: Double,
    val attributes: Attributes,
    val context: Context?,
)

/**
 * A [aws.smithy.kotlin.runtime.telemetry.TelemetryProvider] test double that records every metric measurement
 * (histogram or counter) along with the attributes and telemetry context it was emitted with.
 *
 * [activeContext] is returned from [ContextManager.current] so tests can assert that the "current" context is
 * threaded through to metric recordings.
 */
@OptIn(ExperimentalApi::class)
internal class RecordingTelemetryProvider : AbstractTelemetryProvider() {
    val records = mutableListOf<MetricRecord>()

    /**
     * A distinct, non-default context instance returned by the [contextManager]. Tests assert recorded metrics
     * carry this exact instance to prove the current context is propagated.
     */
    val activeContext: Context = object : AbstractContext() {}

    override val contextManager: ContextManager = object : ContextManager {
        override fun current(): Context = activeContext
    }

    override val meterProvider: MeterProvider = object : AbstractMeterProvider() {
        override fun getOrCreateMeter(scope: String): Meter = RecordingMeter(records)
    }

    /**
     * Returns all recordings for the instrument with the given [name].
     */
    fun recordsFor(name: String): List<MetricRecord> = records.filter { it.name == name }
}

private class RecordingMeter(private val sink: MutableList<MetricRecord>) : AbstractMeter() {
    override fun createDoubleHistogram(
        name: String,
        units: String?,
        description: String?,
    ): DoubleHistogram = RecordingDoubleHistogram(name, sink)

    override fun createMonotonicCounter(
        name: String,
        units: String?,
        description: String?,
    ): MonotonicCounter = RecordingMonotonicCounter(name, sink)
}

private class RecordingDoubleHistogram(
    private val name: String,
    private val sink: MutableList<MetricRecord>,
) : Histogram<Double> {
    override fun record(value: Double, attributes: Attributes, context: Context?) {
        sink += MetricRecord(name, value, attributes, context)
    }
}

private class RecordingMonotonicCounter(
    private val name: String,
    private val sink: MutableList<MetricRecord>,
) : MonotonicCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        sink += MetricRecord(name, value.toDouble(), attributes, context)
    }
}
