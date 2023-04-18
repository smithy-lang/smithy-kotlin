/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.AttributeKey

// See OTeL [Attribute naming](https://opentelemetry.io/docs/reference/specification/common/attribute-naming/)
// for guidelines to follow when adding new (span) attribute names

public object TraceSpanAttributes {
    /**
     * The name of the client
     */
    public val ClientName: AttributeKey<String> = AttributeKey("aws.smithy.kotlin.client_name")

    /**
     * The status of the current span.
     */
    internal val SpanStatus: AttributeKey<TraceSpanStatus> = AttributeKey("aws.smithy.kotlin.span_status")
}

/**
 * Get the current status of this span if it has been set.
 */
public var TraceSpan.spanStatus: TraceSpanStatus
    get() = attributes.getOrNull(TraceSpanAttributes.SpanStatus) ?: TraceSpanStatus.UNSET
    set(value) {
        attributes[TraceSpanAttributes.SpanStatus] = value
    }
