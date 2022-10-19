/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineContext] element that carries a [TraceSpan].
 * @param traceSpan The active trace span for this context.
 */
public class TraceSpanContextElement(public val traceSpan: TraceSpan) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = TraceSpanContextElement

    public companion object : CoroutineContext.Key<TraceSpanContextElement>
}

/**
 * Gets the active [TraceSpan] from this [CoroutineContext].
 */
public val CoroutineContext.traceSpan: TraceSpan
    get() = get(TraceSpanContextElement)!!.traceSpan

/**
 * Runs a block of code within the context of a child [TraceSpan]. This call pushes the new child trace span onto the
 * coroutine context before executing [block] and restores the parent span after the block completes (whether
 * successfully or exceptionally).
 */
public suspend inline fun <R> CoroutineContext.withChildTraceSpan(
    id: String,
    crossinline block: suspend CoroutineScope.() -> R,
): R {
    val existingSpan = checkNotNull(get(TraceSpanContextElement)?.traceSpan) { "Missing an active trace span" }
    val childSpan = existingSpan.child(id)
    return try {
        withContext(TraceSpanContextElement(childSpan)) {
            block()
        }
    } finally {
        childSpan.close()
    }
}

@InternalApi
public suspend inline fun <R> CoroutineContext.withRootTraceSpan(
    rootSpan: TraceSpan,
    crossinline block: suspend CoroutineScope.() -> R,
): R =
    try {
        val existingSpan = get(TraceSpanContextElement)?.traceSpan
        check(existingSpan == null || existingSpan == rootSpan.parent) {
            "This method may only be called when no current span exists or the new span is a child of the active span"
        }

        withContext(TraceSpanContextElement(rootSpan)) {
            block()
        }
    } finally {
        rootSpan.close()
    }

/**
 * Logs a message in the [TraceSpan] of this [CoroutineContext].
 * @param level The level (or severity) of this event
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun CoroutineContext.log(
    level: EventLevel,
    sourceComponent: String,
    ex: Throwable? = null,
    content: () -> Any?,
): Unit = traceSpan.log(level, sourceComponent, ex, content)

/**
 * Logs a message in the [TraceSpan] of this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param level The level (or severity) of this event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> CoroutineContext.log(
    level: EventLevel,
    ex: Throwable? = null,
    noinline content: () -> Any?,
): Unit = traceSpan.log<T>(level, ex, content)

/**
 * Logs a fatal message in the [TraceSpan] of this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun CoroutineContext.fatal(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Fatal, sourceComponent, ex, content)

/**
 * Logs a fatal message in the [TraceSpan] of this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> CoroutineContext.fatal(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Fatal, ex, content)

/**
 * Logs an error message in the [TraceSpan] of this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun CoroutineContext.error(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Error, sourceComponent, ex, content)

/**
 * Logs an error message in the [TraceSpan] of this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> CoroutineContext.error(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Error, ex, content)

/**
 * Logs a warning message in the [TraceSpan] of this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun CoroutineContext.warn(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Warning, sourceComponent, ex, content)

/**
 * Logs a warning message in the [TraceSpan] of this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> CoroutineContext.warn(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Warning, ex, content)

/**
 * Logs an info message in the [TraceSpan] of this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun CoroutineContext.info(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Info, sourceComponent, ex, content)

/**
 * Logs an info message in the [TraceSpan] of this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> CoroutineContext.info(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Info, ex, content)

/**
 * Logs a debug message in the [TraceSpan] of this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun CoroutineContext.debug(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Debug, sourceComponent, ex, content)

/**
 * Logs a debug message in the [TraceSpan] of this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> CoroutineContext.debug(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Debug, ex, content)

/**
 * Logs a trace-level message in the [TraceSpan] of this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun CoroutineContext.trace(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    traceSpan.log(EventLevel.Trace, sourceComponent, ex, content)

/**
 * Logs a trace-level message in the [TraceSpan] of this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> CoroutineContext.trace(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    traceSpan.log<T>(EventLevel.Trace, ex, content)
