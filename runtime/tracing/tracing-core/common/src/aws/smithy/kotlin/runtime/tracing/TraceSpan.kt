/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.logging.Logger
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

public fun TraceSpan.logger(forSourceComponent: String): Logger = TraceSpanLogger(this, forSourceComponent)

private class TraceSpanLogger(private val span: TraceSpan, private val sourceComponent: String) : Logger {
    fun log(level: EventLevel, ex: Throwable? = null, msg: () -> Any?) {
        val event = TraceEvent(
            level,
            sourceComponent,
            Instant.now(),
            "thread-id", // TODO
            TraceEventData.Message(ex, msg),
        )
        span.postEvent(event)
    }

    override fun error(msg: () -> Any?) = log(EventLevel.Error, null, msg)
    override fun error(t: Throwable?, msg: () -> Any?) = log(EventLevel.Error, t, msg)

    override fun warn(msg: () -> Any?) = log(EventLevel.Warning, null, msg)
    override fun warn(t: Throwable?, msg: () -> Any?) = log(EventLevel.Warning, t, msg)

    override fun info(msg: () -> Any?) = log(EventLevel.Info, null, msg)
    override fun info(t: Throwable?, msg: () -> Any?) = log(EventLevel.Info, t, msg)

    override fun debug(msg: () -> Any?) = log(EventLevel.Debug, null, msg)
    override fun debug(t: Throwable?, msg: () -> Any?) = log(EventLevel.Debug, t, msg)

    override fun trace(msg: () -> Any?) = log(EventLevel.Trace, null, msg)
    override fun trace(t: Throwable?, msg: () -> Any?) = log(EventLevel.Trace, t, msg)

    override fun <T : Throwable> throwing(throwable: T): T = TODO("Not yet implemented")
    override fun <T : Throwable> catching(throwable: T) = TODO("Not yet implemented")
}
