/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.telemetry.context.Context

/**
 * Callback parameter passed to record a gauge value
 */
public interface AsyncMeasurement<T : Number> {
    /**
     * Record a gauge value
     * @param value the value to record
     * @param attributes attributes to associate with this measurement
     * @param context (Optional) trace context to associate with this measurement
     */
    public fun record(
        value: T,
        attributes: Attributes = emptyAttributes(),
        context: Context? = null,
    )
}

public typealias LongAsyncMeasurement = AsyncMeasurement<Long>
public typealias LongGaugeCallback = (LongAsyncMeasurement) -> Unit
public typealias LongUpDownCounterCallback = (LongAsyncMeasurement) -> Unit

public typealias DoubleAsyncMeasurement = AsyncMeasurement<Double>
public typealias DoubleGaugeCallback = (DoubleAsyncMeasurement) -> Unit

/**
 * A handle to a registered async measurement (e.g. Gauge or AsyncUpDownCounter)
 */
public interface AsyncMeasurementHandle {
    public companion object {
        /**
         * An [AsyncMeasurementHandle] that does nothing
         */
        public val None: AsyncMeasurementHandle = object : AbstractAsyncMeasurementHandle() { }
    }

    /**
     * Stop recording this async value. The registered callback function will
     * stop being invoked after calling this function.
     */
    public fun stop()
}
