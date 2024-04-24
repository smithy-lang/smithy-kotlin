/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import kotlin.time.Duration
import kotlin.time.measureTimedValue

public interface Histogram<T : Number> {
    public companion object {
        /**
         * A [DoubleHistogram] that does nothing
         */
        public val DoubleNone: DoubleHistogram = object : AbstractDoubleHistogram() { }

        /**
         * A [LongHistogram] that does nothing
         */
        public val LongNone: LongHistogram = object : AbstractLongHistogram() { }
    }

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
    val ms = value.inWholeNanoseconds.toDouble()
    val sec = ms / 1_000_000_000
    record(sec, attributes, context)
}

/**
 * Measure how long [block] takes to execute and record the duration in seconds using millisecond precision.
 *
 * @param attributes attributes to associate with this measurement
 * @param context (Optional) trace context to associate with this measurement
 * @param block the code to execute and return a result from
 */
public inline fun <T> DoubleHistogram.measureSeconds(attributes: Attributes = emptyAttributes(), context: Context? = null, block: () -> T): T {
    val tv = measureTimedValue(block)
    recordSeconds(tv.duration, attributes, context)
    return tv.value
}
