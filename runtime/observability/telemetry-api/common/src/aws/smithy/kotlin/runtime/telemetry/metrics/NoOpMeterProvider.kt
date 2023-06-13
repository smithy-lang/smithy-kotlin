/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.util.Attributes

internal object NoOpMeterProvider : MeterProvider {
    override fun getMeter(scope: String, attributes: Attributes): Meter = NoOpMeter
}

/**
 * A meter that does nothing
 */
private object NoOpMeter : Meter {
    override fun createUpDownCounter(name: String, units: String?, description: String?): UpDownCounter = NoOpUpDownCounter

    override fun createMonotonicCounter(name: String, units: String?, description: String?): MonotonicCounter = NoOpMonotonicCounter

    override fun createLongHistogram(name: String, units: String?, description: String?): LongHistogram = NoOpLongHistogram

    override fun createDoubleHistogram(name: String, units: String?, description: String?): DoubleHistogram = NoOpDoubleHistogram

    override fun createLongGauge(
        name: String,
        callback: LongGaugeCallback,
        units: String?,
        description: String?,
    ): GaugeHandle = NoOpGaugeHandle

    override fun createDoubleGauge(
        name: String,
        callback: DoubleGaugeCallback,
        units: String?,
        description: String?,
    ): GaugeHandle = NoOpGaugeHandle
}

private object NoOpUpDownCounter : UpDownCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {}
}
private object NoOpMonotonicCounter : MonotonicCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {}
}

private object NoOpLongHistogram : LongHistogram {
    override fun record(value: Long, attributes: Attributes, context: Context?) {}
}
private object NoOpDoubleHistogram : DoubleHistogram {
    override fun record(value: Double, attributes: Attributes, context: Context?) {}
}

private object NoOpGaugeHandle : GaugeHandle {
    override fun stop() {}
}
