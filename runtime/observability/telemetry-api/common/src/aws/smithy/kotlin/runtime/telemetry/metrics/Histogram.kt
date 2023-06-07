/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.telemetry.trace.TraceContext
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

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
        context: TraceContext? = null,
    )
}
public typealias LongHistogram = Histogram<Long>
public typealias DoubleHistogram = Histogram<Double>
