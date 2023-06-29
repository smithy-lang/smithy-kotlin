/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes
import kotlin.time.Duration

public interface Histogram<T : Number> {
    /**
     * Updates the statistics with a value
     *
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
public typealias LongHistogram = Histogram<Long>
public typealias DoubleHistogram = Histogram<Double>

/**
 * Record a duration in seconds using millisecond precision.
 *
 * @param value the duration to record
 * @param attributes attributes to associate with this measurement
 * @param context (Optional) trace context to associate with this measurement
 */
@InternalApi
public fun DoubleHistogram.recordSeconds(value: Duration, attributes: Attributes = emptyAttributes(), context: Context? = null) {
    val ms = value.inWholeMilliseconds.toDouble()
    val sec = ms / 1000
    record(sec, attributes, context)
}
