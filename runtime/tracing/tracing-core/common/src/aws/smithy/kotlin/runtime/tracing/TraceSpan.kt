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
     * The name of the span
     */
    public val name: String

    /**
     * The span's context
     */
    public val context: TraceContext

    /**
     * The span's status
     */
    public var spanStatus: TraceSpanStatus

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

    /**
     * Set the attribute with the given name to [value]
     * NOTE: Attributes should generally be a primitive type: boolean, string, long, double or List of the same.
     */
    public fun <T : Any> setAttr(key: String, value: T)
}
