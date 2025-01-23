/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.ism

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.metrics.*
import aws.smithy.kotlin.runtime.time.Clock

internal class IsmMeterProvider internal constructor(
    private val listener: MetricListener,
    private val clock: Clock,
) : MeterProvider {
    override fun getOrCreateMeter(scope: String): Meter = IsmMeter(listener, clock)
}

private class IsmMeter(val listener: MetricListener, val clock: Clock) : Meter {
    override fun createUpDownCounter(name: String, units: String?, description: String?) =
        IsmUpDownCounter(listener, name, units, description, clock)

    override fun createMonotonicCounter(name: String, units: String?, description: String?) =
        IsmMonotonicCounter(listener, name, units, description, clock)

    override fun createLongHistogram(name: String, units: String?, description: String?) =
        IsmLongHistogram(listener, name, units, description, clock)

    override fun createDoubleHistogram(name: String, units: String?, description: String?) =
        IsmDoubleHistogram(listener, name, units, description, clock)

    override fun createAsyncUpDownCounter(
        name: String,
        callback: LongUpDownCounterCallback,
        units: String?,
        description: String?
    ) = TODO("Not yet implemented")

    override fun createLongGauge(
        name: String,
        callback: LongGaugeCallback,
        units: String?,
        description: String?,
    ) = TODO("Not yet implemented")

    override fun createDoubleGauge(
        name: String,
        callback: DoubleGaugeCallback,
        units: String?,
        description: String?,
    ) = TODO("Not yet implemented")
}

private abstract class AbstractInstrument(
    val listener: MetricListener,
    val name: String,
    val units: String?,
    val description: String?,
    val clock: Clock,
) {
    protected fun <T> emit(value: T, attributes: Attributes, context: Context?) {
        if (context != null) {
            val record = MetricRecord(name, units, description, value, attributes, clock.now())
            listener.onMetrics(context, record)
        }
    }
}

private class IsmUpDownCounter(
    listener: MetricListener,
    name: String,
    units: String?,
    description: String?,
    clock: Clock,
) : AbstractInstrument(listener, name, units, description, clock), UpDownCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) = emit(value, attributes, context)
}

private class IsmMonotonicCounter(
    listener: MetricListener,
    name: String,
    units: String?,
    description: String?,
    clock: Clock,
) : AbstractInstrument(listener, name, units, description, clock), MonotonicCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) = emit(value, attributes, context)
}

private class IsmLongHistogram(
    listener: MetricListener,
    name: String,
    units: String?,
    description: String?,
    clock: Clock,
) : AbstractInstrument(listener, name, units, description, clock), LongHistogram {
    override fun record(value: Long, attributes: Attributes, context: Context?) = emit(value, attributes, context)
}

private class IsmDoubleHistogram(
    listener: MetricListener,
    name: String,
    units: String?,
    description: String?,
    clock: Clock,
) : AbstractInstrument(listener, name, units, description, clock), DoubleHistogram {
    override fun record(value: Double, attributes: Attributes, context: Context?) = emit(value, attributes, context)
}
