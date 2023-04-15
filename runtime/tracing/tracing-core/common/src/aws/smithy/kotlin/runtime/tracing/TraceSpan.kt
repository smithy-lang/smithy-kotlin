/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.util.MutableAttributes
import aws.smithy.kotlin.runtime.util.get
import aws.smithy.kotlin.runtime.util.set

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
     * Attributes associated with this span
     */
    public val attributes: MutableAttributes

    /**
     * The current status of this span.
     */
    public var spanStatus: TraceSpanStatus

    /**
     * Creates a new child span with the given ID.
     * @param id The id for the new span. IDs should be unique among sibling spans within the same parent.
     * @return The new child span.
     */
    public fun child(id: String): TraceSpan

    /**
     * Records a new event that has occurred within the logical context of this span.
     * @param event The event to record.
     */
    public fun postEvent(event: TraceEvent)
}

/**
 * Set a string attribute on the current span
 */
public fun TraceSpan.setAttribute(key: String, value: String): Unit = attributes.set(key, value)

/**
 * Get a string attribute from the current span
 */
public fun TraceSpan.getStringAttribute(key: String): String = attributes.get(key)

/**
 * Set a long attribute on the current span
 */
public fun TraceSpan.setAttribute(key: String, value: Long): Unit = attributes.set(key, value)

/**
 * Get a long attribute from the current span
 */
public fun TraceSpan.getLongAttribute(key: String): Long = attributes.get(key)

/**
 * Set a boolean attribute on the current span
 */
public fun TraceSpan.setAttribute(key: String, value: Boolean): Unit = attributes.set(key, value)

/**
 * Get a boolean attribute from the current span
 */
public fun TraceSpan.getBooleanAttribute(key: String): Boolean = attributes.get(key)
