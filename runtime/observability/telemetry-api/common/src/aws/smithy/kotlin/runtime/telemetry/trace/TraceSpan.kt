/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * Represents a single operation/task within a trace. Each trace contains a root span and
 * optionally one or more child spans.
 */
public interface TraceSpan {
    /**
     * The name of the span
     */
    public val name: String

    /**
     * The span's context
     */
    public val context: Context

    /**
     * Set an attribute on the span
     * @param key the attribute key to use
     * @param value the value to associate with the key
     */
    public fun <T : Any> setAttribute(key: AttributeKey<T>, value: T)

    /**
     * Set all [attributes] on the span.
     * @param attributes collection of attributes to set on the span
     */
    public fun setAttributes(attributes: Attributes)

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
    public fun end()
}
