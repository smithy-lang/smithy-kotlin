/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.telemetry.context.Scope

/**
 * Represents a single operation/task within a trace. Each trace contains a root span and
 * optionally one or more child spans.
 */
public interface TraceSpan : Scope {
    /**
     * The immutable tracing context this span belongs to
     */
    public val spanContext: SpanContext

    /**
     * Set an attribute on the span
     * @param key the attribute key to use
     * @param value the value to associate with the key
     */
    public operator fun <T : Any> set(key: AttributeKey<T>, value: T)

    /**
     * Merge all attributes from [attributes] into this span's attributes (overriding any previously set values
     * with the same keys).
     * @param attributes collection of attributes to merge into current span's attributes
     */
    public fun mergeAttributes(attributes: Attributes)

    /**
     * Add an event to this span
     * @param name the name of the event
     * @param attributes attributes to associate with this event
     */
    public fun emitEvent(name: String, attributes: Attributes = emptyAttributes())

    /**
     * Set the span status
     * @param status the status to set
     */
    public fun setStatus(status: SpanStatus)

    /**
     * Marks the end of this span's execution. This MUST be called when the unit
     * of work the span represents has finished.
     */
    override fun close()
}
