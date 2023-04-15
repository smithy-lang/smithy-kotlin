/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

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
    get() = get(TraceSpanContextElement)?.traceSpan ?: NoOpTraceSpan

/**
 * Runs a block of code within the context of a child [TraceSpan]. This call pushes the new child trace span onto the
 * coroutine context before executing [block] and restores the parent span after the block completes (whether
 * successfully or exceptionally).
 */
public suspend inline fun <R> withSpan(
    id: String,
    crossinline block: suspend CoroutineScope.() -> R,
): R = withSpan(coroutineContext.traceSpan.child(id), block)

/**
 * Runs a block of code within the context of the given [span]. This call pushes the trace span onto the
 * coroutine context before executing [block] and restores the context after.
 */
@InternalApi
public suspend inline fun <R> withSpan(
    span: TraceSpan,
    crossinline block: suspend CoroutineScope.() -> R,
): R =
    try {
        withContext(TraceSpanContextElement(span)) {
            block()
        }
    } catch (ex: Exception) {
        if (ex !is CancellationException && span.spanStatus == TraceSpanStatus.UNSET) {
            span.spanStatus = TraceSpanStatus.ERROR
        }
        throw ex
    } finally {
        span.close()
    }

/**
 * Runs the block of code within the context of a new [TraceSpan]. The span is either a child of an existing trace span
 * or a new root span created the given [Tracer]. This call pushes the trace span onto the coroutine context before
 * executing [block] and restores the context after.
 */
@InternalApi
public suspend inline fun <R> Tracer.withSpan(
    id: String,
    crossinline block: suspend CoroutineScope.() -> R,
): R {
    val existingSpan = coroutineContext[TraceSpanContextElement]?.traceSpan
    val span = existingSpan?.child(id) ?: createRootSpan(id)
    return withSpan(span, block)
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
