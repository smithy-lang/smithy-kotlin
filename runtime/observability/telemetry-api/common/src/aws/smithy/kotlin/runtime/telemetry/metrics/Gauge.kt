/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

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
        context: Context,
    )
}

public typealias LongAsyncMeasurement = AsyncMeasurement<Long>
public typealias LongGaugeCallback = (LongAsyncMeasurement) -> Unit

public typealias DoubleAsyncMeasurement = AsyncMeasurement<Double>
public typealias DoubleGaugeCallback = (DoubleAsyncMeasurement) -> Unit

/**
 * A handle to a registered guage
 */
public interface GaugeHandle {
    /**
     * Stop recording this gauge value. The registered callback function will
     * stop being invoked after calling this function.
     */
    public fun stop()
}
