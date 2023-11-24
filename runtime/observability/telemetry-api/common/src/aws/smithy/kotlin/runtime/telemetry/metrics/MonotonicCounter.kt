/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.telemetry.context.Context

public interface MonotonicCounter {
    /**
     * Records a value
     *
     * @param value the delta amount to record (MUST be positive)
     * @param attributes attributes to associate with this measurement
     * @param context (Optional) trace context to associate with this measurement
     */
    public fun add(
        value: Long,
        attributes: Attributes = emptyAttributes(),
        context: Context? = null,
    )
}
