/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics

/**
 * An abstract implementation of a meter. By default, this class uses no-op implementations for all members unless
 * overridden in a subclass.
 */
public abstract class AbstractMeter : Meter {
    override fun createUpDownCounter(name: String, units: String?, description: String?): UpDownCounter = UpDownCounter.None

    override fun createAsyncUpDownCounter(
        name: String,
        callback: LongUpDownCounterCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = AsyncMeasurementHandle.None

    override fun createMonotonicCounter(name: String, units: String?, description: String?): MonotonicCounter = MonotonicCounter.None

    override fun createLongHistogram(name: String, units: String?, description: String?): LongHistogram = Histogram.LongNone

    override fun createDoubleHistogram(name: String, units: String?, description: String?): DoubleHistogram = Histogram.DoubleNone

    override fun createLongGauge(
        name: String,
        callback: LongGaugeCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = AsyncMeasurementHandle.None

    override fun createDoubleGauge(
        name: String,
        callback: DoubleGaugeCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = AsyncMeasurementHandle.None
}
