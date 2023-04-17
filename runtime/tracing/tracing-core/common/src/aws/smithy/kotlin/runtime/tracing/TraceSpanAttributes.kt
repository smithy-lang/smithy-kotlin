/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.AttributeKey

internal object TraceSpanAttributes {

    /**
     * The status of the current span.
     */
    val SpanStatus: AttributeKey<TraceSpanStatus> = AttributeKey("aws.smithy.kotlin#TraceSpanStatus")
}

/**
 * Get the current status of this span if it has been set.
 */
public var TraceSpan.spanStatus: TraceSpanStatus
    get() = attributes.getOrNull(TraceSpanAttributes.SpanStatus) ?: TraceSpanStatus.UNSET
    set(value) {
        attributes[TraceSpanAttributes.SpanStatus] = value
    }
