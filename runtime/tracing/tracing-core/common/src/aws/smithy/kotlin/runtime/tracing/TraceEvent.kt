/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.time.Instant
import kotlin.time.Duration

/**
 * Defines the level or severity of the event. This will be checked by probes when determining how (or whether) to
 * handle events.
 */
public enum class EventLevel {
    Fatal,
    Error,
    Warning,
    Info,
    Debug,
    Trace,
    ;
}

/**
 * The core data of a trace event.
 */
public sealed interface TraceEventData {
    /**
     * A message that contains freeform text and optionally an exception.
     * @param exception An optional exception which explains the message.
     * @param text: A lambda which provides the text of the message. This text does not need to include any data from
     * the exception (if any), which may be concatenated later based on probe behavior.
     */
    public data class Message(public val exception: Throwable? = null, public val text: () -> String) : TraceEventData

    /**
     * An interface for event data which are attributed to a named metric.
     */
    public sealed interface Metric : TraceEventData {
        /**
         * The name of the metric to which this data is attributed.
         */
        public val metric: String
    }

    /**
     * A metric that consists of an enumeration or total.
     * @param metric The name of the metric.
     * @param count The value of the count.
     * @param T The type of number utilized by the count.
     */
    public data class Count<T : Number>(override val metric: String, public val count: () -> T) : Metric

    /**
     * A metric that consists of a measured duration.
     * @param metric The name of the metric.
     * @param duration The length of time measured by the metric.
     */
    public data class Timespan(override val metric: String, public val duration: () -> Duration) : Metric
}

/**
 * A single trace event which records the operation of the system.
 */
public data class TraceEvent(
    /**
     * The level (or severity) of this event.
     */
    public val level: EventLevel,

    /**
     * The name of the component that generated the event.
     */
    public val sourceComponent: String,

    /**
     * The time at which the event occurred or was recorded.
     */
    public val timestamp: Instant,

    /**
     * The ID of the thread on which this event occurred or was recorded.
     */
    public val threadId: String,

    /**
     * The contents of the event.
     */
    public val data: TraceEventData,
)
