/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.util.MutableAttributes

/**
 * Defines a logical lifecycle within which events may occur. Spans are typically created before some notable operation
 * and closed after that operation is completed. Such operations may be very long and may involve many sub-operations,
 * some of which may warrant child spans.
 */
public interface TraceSpan : Closeable {
    /**
     * The identifier for this span, which should be unique among sibling spans within the same parent. Trace span IDs
     * may be used by probes to collate or decorate event output.
     */
    public val id: String

    /**
     * The parent span for this child span (if any).
     */
    public val parent: TraceSpan?

    /**
     * Attributes associated with the span that describes both built-in (e.g. name, trace ID, status, etc) and
     * user defined characteristics of the span.
     */
    public val attributes: MutableAttributes

    /**
     * Metadata for this span
     */
    public val metadata: TraceSpanMetadata

    /**
     * Creates a new child span with the given name.
     * @param name The name for the new span.
     * @return The new child span.
     */
    public fun child(name: String): TraceSpan

    /**
     * Records a new event that has occurred within the logical context of this span.
     * @param event The event to record.
     */
    public fun postEvent(event: TraceEvent)
}

/**
 * Metadata describing a span
 * @param traceId the trace this span belongs to
 * @param name the name of the span
 */
public data class TraceSpanMetadata(
    val traceId: String,
    val name: String,
)
