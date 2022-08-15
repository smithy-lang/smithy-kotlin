/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.time.Instant

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

/**
 * Records a single new event that has occurred within the logical context of this span.
 * @param event The event to record.
 */
public fun TraceSpan.postEvent(event: TraceEvent): Unit = postEvents(listOf(event))

/**
 * Records a log message that has occurred within the logical context of this span.
 * @param level The [EventLevel] of the log message.
 * @param sourceComponent The name of the component that generated the event.
 * @param ex An optional exception which explains the message.
 * @param msg A lambda which provides the text of the message. This text does not need to include any data from the
 * exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.log(
    level: EventLevel,
    sourceComponent: String,
    ex: Throwable? = null,
    msg: () -> String,
) {
    val event = TraceEvent(
        level,
        sourceComponent,
        Instant.now(),
        "thread-id", // TODO
        TraceEventData.Message(ex, msg),
    )
    postEvent(event)
}

/**
 * Records a fatal log message that has occurred within the logical context of this span.
 * @param sourceComponent The name of the component that generated the event.
 * @param ex An optional exception which explains the message.
 * @param msg A lambda which provides the text of the message. This text does not need to include any data from the
 * exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.fatal(sourceComponent: String, ex: Throwable? = null, msg: () -> String): Unit =
    log(EventLevel.Fatal, sourceComponent, ex, msg)

/**
 * Records an error log message that has occurred within the logical context of this span.
 * @param sourceComponent The name of the component that generated the event.
 * @param ex An optional exception which explains the message.
 * @param msg A lambda which provides the text of the message. This text does not need to include any data from the
 * exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.error(sourceComponent: String, ex: Throwable? = null, msg: () -> String): Unit =
    log(EventLevel.Error, sourceComponent, ex, msg)

/**
 * Records a warning log message that has occurred within the logical context of this span.
 * @param sourceComponent The name of the component that generated the event.
 * @param ex An optional exception which explains the message.
 * @param msg A lambda which provides the text of the message. This text does not need to include any data from the
 * exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.warn(sourceComponent: String, ex: Throwable? = null, msg: () -> String): Unit =
    log(EventLevel.Warning, sourceComponent, ex, msg)

/**
 * Records an info log message that has occurred within the logical context of this span.
 * @param sourceComponent The name of the component that generated the event.
 * @param ex An optional exception which explains the message.
 * @param msg A lambda which provides the text of the message. This text does not need to include any data from the
 * exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.info(sourceComponent: String, ex: Throwable? = null, msg: () -> String): Unit =
    log(EventLevel.Info, sourceComponent, ex, msg)

/**
 * Records a debug log message that has occurred within the logical context of this span.
 * @param sourceComponent The name of the component that generated the event.
 * @param ex An optional exception which explains the message.
 * @param msg A lambda which provides the text of the message. This text does not need to include any data from the
 * exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.debug(sourceComponent: String, ex: Throwable? = null, msg: () -> String): Unit =
    log(EventLevel.Debug, sourceComponent, ex, msg)

/**
 * Records a trace-level log message that has occurred within the logical context of this span.
 * @param sourceComponent The name of the component that generated the event.
 * @param ex An optional exception which explains the message.
 * @param msg A lambda which provides the text of the message. This text does not need to include any data from the
 * exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.trace(sourceComponent: String, ex: Throwable? = null, msg: () -> String): Unit =
    log(EventLevel.Trace, sourceComponent, ex, msg)
