/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.get

/**
 * An object that holds context keys for tracing-related properties.
 */
public object TracingContext {
    /**
     * The active [TraceSpan] that receives events.
     */
    public val TraceSpan: AttributeKey<TraceSpan> = AttributeKey("TraceSpan")
}

/**
 * Gets the active [TraceSpan] for this [ExecutionContext].
 */
public val ExecutionContext.traceSpan: TraceSpan
    get() = get(TracingContext.TraceSpan)

/**
 * Pushes a new child [TraceSpan] into this [ExecutionContext]. The child span will have the current span as its parent.
 * This call should be paired with a later call to [popChildTraceSpan].
 * @param The id of the child span.
 */
public fun ExecutionContext.pushChildTraceSpan(id: String) {
    val existingSpan = checkNotNull(getOrNull(TracingContext.TraceSpan)) { "Missing an active trace span" }
    set(TracingContext.TraceSpan, existingSpan.child(id))
}

/**
 * Closes and pops the current child [TraceSpan] from this [ExecutionContext], restoring the parent span as the active
 * span. This call should be paired with a prior call to [pushChildTraceSpan].
 */
public fun ExecutionContext.popChildTraceSpan() {
    val existingSpan = checkNotNull(getOrNull(TracingContext.TraceSpan)) { "Missing an active trace span" }
    existingSpan.close()

    val parentSpan = checkNotNull(existingSpan.parent) { "The active trace span has no parent and cannot be popped" }
    set(TracingContext.TraceSpan, parentSpan)
}

/**
 * Runs a block of code within the context of a child [TraceSpan]. This call pushes the new child trace span into the
 * context before executing [block] and restores the parent span after the block completes (whether successfully or
 * exceptionally).
 * @param id The id of the new child span.
 * @param block The block of code to execute. Once the block is complete, the previous span will be restored.
 */
public inline fun <R> ExecutionContext.withChildSpan(id: String, block: () -> R): R {
    pushChildTraceSpan(id)
    try {
        return block()
    } finally {
        popChildTraceSpan()
    }
}

@InternalApi
public inline fun <R> ExecutionContext.withRootSpan(rootSpan: TraceSpan, block: () -> R): R =
    try {
        check(getOrNull(TracingContext.TraceSpan) == null) { "Cannot push new root span when existing span is active" }
        set(TracingContext.TraceSpan, rootSpan)
        try {
            block()
        } finally {
            remove(TracingContext.TraceSpan)
        }
    } finally {
        rootSpan.close()
    }

/**
 * Logs a message in the [TraceSpan] of this [ExecutionContext].
 * @param level The level (or severity) of this event
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun ExecutionContext.log(
    level: EventLevel,
    sourceComponent: String,
    ex: Throwable? = null,
    content: () -> Any?,
): Unit = traceSpan.log(level, sourceComponent, ex, content)

/**
 * Logs a message in the [TraceSpan] of this [ExecutionContext].
 * @param T The class to use for the name of the component that generated the event
 * @param level The level (or severity) of this event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> ExecutionContext.log(
    level: EventLevel,
    ex: Throwable? = null,
    noinline content: () -> Any?,
): Unit = traceSpan.log<T>(level, ex, content)

/**
 * Logs a fatal message in the [TraceSpan] of this [ExecutionContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun ExecutionContext.fatal(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Fatal, sourceComponent, ex, content)

/**
 * Logs a fatal message in the [TraceSpan] of this [ExecutionContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> ExecutionContext.fatal(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Fatal, ex, content)

/**
 * Logs an error message in the [TraceSpan] of this [ExecutionContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun ExecutionContext.error(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Error, sourceComponent, ex, content)

/**
 * Logs an error message in the [TraceSpan] of this [ExecutionContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> ExecutionContext.error(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Error, ex, content)

/**
 * Logs a warning message in the [TraceSpan] of this [ExecutionContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun ExecutionContext.warn(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Warning, sourceComponent, ex, content)

/**
 * Logs a warning message in the [TraceSpan] of this [ExecutionContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> ExecutionContext.warn(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Warning, ex, content)

/**
 * Logs an info message in the [TraceSpan] of this [ExecutionContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun ExecutionContext.info(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Info, sourceComponent, ex, content)

/**
 * Logs an info message in the [TraceSpan] of this [ExecutionContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> ExecutionContext.info(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Info, ex, content)

/**
 * Logs a debug message in the [TraceSpan] of this [ExecutionContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun ExecutionContext.debug(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Debug, sourceComponent, ex, content)

/**
 * Logs a debug message in the [TraceSpan] of this [ExecutionContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> ExecutionContext.debug(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Debug, ex, content)

/**
 * Logs a trace-level message in the [TraceSpan] of this [ExecutionContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun ExecutionContext.trace(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Trace, sourceComponent, ex, content)

/**
 * Logs a trace-level message in the [TraceSpan] of this [ExecutionContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> ExecutionContext.trace(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Trace, ex, content)
