/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.io.Closeable

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
     * Creates a new child span with the given ID.
     * @param id The id for the new span. IDs should be unique among sibling spans within the same parent.
     * @return The new child span.
     */
    public fun child(id: String): TraceSpan

    /**
     * Records new events that have occurred within the logical context of this span.
     * @param events The events to record.
     */
    public fun postEvents(events: Iterable<TraceEvent>)
}
