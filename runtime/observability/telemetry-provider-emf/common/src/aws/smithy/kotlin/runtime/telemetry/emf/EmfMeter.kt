/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.telemetry.metrics.AsyncMeasurementHandle
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleGaugeCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongGaugeCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.LongHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongUpDownCounterCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.Meter
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter
import aws.smithy.kotlin.runtime.telemetry.metrics.UpDownCounter

internal class EmfMeter(
    private val namespace: String,
    private val logGroupName: String?,
) : Meter {

    override fun createUpDownCounter(name: String, units: String?, description: String?): UpDownCounter = EmfUpDownCounter(namespace, logGroupName, name, units)

    override fun createAsyncUpDownCounter(
        name: String,
        callback: LongUpDownCounterCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = AsyncMeasurementHandle.None

    override fun createMonotonicCounter(name: String, units: String?, description: String?): MonotonicCounter = EmfMonotonicCounter(namespace, logGroupName, name, units)

    override fun createLongHistogram(name: String, units: String?, description: String?): LongHistogram = EmfLongHistogram(namespace, logGroupName, name, units)

    override fun createDoubleHistogram(name: String, units: String?, description: String?): DoubleHistogram = EmfDoubleHistogram(namespace, logGroupName, name, units)

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
